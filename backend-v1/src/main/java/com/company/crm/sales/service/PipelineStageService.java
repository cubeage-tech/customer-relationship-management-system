package com.company.crm.sales.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.sales.dto.request.PipelineStageReqDto;
import com.company.crm.sales.dto.response.PipelineStageResDto;
import com.company.crm.sales.entity.PipelineStage;
import com.company.crm.sales.repository.PipelineStageRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FR-3.2: each tenant's configurable sales stages. Starts as the SRS list (seeded by V30, or on
 * first use for newer tenants). Admins add, rename, reorder and (de)activate open stages; Won and
 * Lost are fixed terminal stages that can only be renamed. Codes never change, so existing
 * opportunities keep a valid stage even after it is deactivated.
 */
@Service
@RequiredArgsConstructor
public class PipelineStageService {

    public static final String WON = "won";
    public static final String LOST = "lost";

    /** Terminal stages sort after every open stage. */
    private static final int WON_ORDER = 1000;
    private static final int LOST_ORDER = 1001;

    private static final List<String[]> DEFAULTS = List.of(
            new String[]{"qualification", "Qualification", "1"}, new String[]{"proposal", "Proposal", "2"},
            new String[]{"negotiation", "Negotiation", "3"},
            new String[]{WON, "Won", String.valueOf(WON_ORDER)}, new String[]{LOST, "Lost", String.valueOf(LOST_ORDER)});

    private final PipelineStageRepository stageRepository;

    public static boolean isTerminal(String code) {
        return WON.equals(code) || LOST.equals(code);
    }

    @Transactional
    public List<PipelineStageResDto> list(User currentUser, boolean includeInactive) {
        return stages(requireTenantId(currentUser)).stream()
                .filter(s -> includeInactive || s.isActive())
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PipelineStageResDto create(User currentUser, PipelineStageReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        List<PipelineStage> existing = stages(tenantId);
        String label = cleanLabel(dto.getLabel());
        if (stageRepository.existsByTenantIdAndLabelIgnoreCase(tenantId, label)) {
            throw ApiException.conflict("A stage with this name already exists");
        }
        PipelineStage stage = new PipelineStage();
        stage.setTenantId(tenantId);
        stage.setCode(uniqueCode(label, existing));
        stage.setLabel(label);
        stage.setActive(true);
        stage.setSortOrder(existing.stream().filter(s -> !isTerminal(s.getCode()))
                .mapToInt(PipelineStage::getSortOrder).max().orElse(0) + 1);
        return toDto(stageRepository.save(stage));
    }

    /** Rename and/or (de)activate. Won/Lost can't be deactivated; at least one open stage stays active. */
    @Transactional
    public PipelineStageResDto update(User currentUser, String code, PipelineStageReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        List<PipelineStage> all = stages(tenantId);
        PipelineStage stage = all.stream().filter(s -> s.getCode().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Stage not found"));
        if (dto.getLabel() != null) {
            String label = cleanLabel(dto.getLabel());
            if (!label.equalsIgnoreCase(stage.getLabel()) && stageRepository.existsByTenantIdAndLabelIgnoreCase(tenantId, label)) {
                throw ApiException.conflict("A stage with this name already exists");
            }
            stage.setLabel(label);
        }
        if (dto.getActive() != null && dto.getActive() != stage.isActive()) {
            if (isTerminal(code)) {
                throw ApiException.badRequest("Won and Lost are always available");
            }
            long activeOpen = all.stream().filter(s -> !isTerminal(s.getCode()) && s.isActive()).count();
            if (!dto.getActive() && activeOpen <= 1) {
                throw ApiException.badRequest("At least one open stage must stay active");
            }
            stage.setActive(dto.getActive());
        }
        return toDto(stageRepository.save(stage));
    }

    /** Reorders the open stages; {@code codes} must list every open stage exactly once. */
    @Transactional
    public List<PipelineStageResDto> reorder(User currentUser, List<String> codes) {
        Long tenantId = requireTenantId(currentUser);
        List<PipelineStage> open = stages(tenantId).stream().filter(s -> !isTerminal(s.getCode())).toList();
        Set<String> expected = new HashSet<>(open.stream().map(PipelineStage::getCode).toList());
        if (codes == null || codes.size() != expected.size() || !expected.equals(new HashSet<>(codes))) {
            throw ApiException.badRequest("List every open stage exactly once");
        }
        for (PipelineStage stage : open) {
            stage.setSortOrder(codes.indexOf(stage.getCode()) + 1);
        }
        stageRepository.saveAll(open);
        return list(currentUser, true);
    }

    /** Throws 400 unless {@code code} is an active stage of the tenant. */
    @Transactional
    public void requireActive(Long tenantId, String code) {
        boolean ok = code != null && stages(tenantId).stream().anyMatch(s -> s.getCode().equals(code) && s.isActive());
        if (!ok) {
            throw ApiException.badRequest("Unknown or inactive opportunity stage: " + code);
        }
    }

    /**
     * VAL-7: how many active stages a move from {@code from} to {@code to} jumps over. 0 for the next
     * stage, any earlier stage, Lost, or Won from the last open stage. Reopening a lost (or no longer
     * active) stage's deal is free, except straight to Won.
     */
    @Transactional
    public int skippedStages(Long tenantId, String from, String to) {
        if (from == null || to.equals(from) || LOST.equals(to)) {
            return 0;
        }
        List<String> order = stages(tenantId).stream()
                .filter(s -> s.isActive() && !LOST.equals(s.getCode()))
                .map(PipelineStage::getCode)
                .toList();
        int fromIndex = order.indexOf(from);
        int toIndex = order.indexOf(to);
        if (fromIndex < 0) {
            return WON.equals(to) ? 1 : 0;
        }
        return Math.max(0, toIndex - fromIndex - 1);
    }

    /** Where new opportunities start: the first active open stage. */
    @Transactional
    public String firstOpenStage(Long tenantId) {
        return stages(tenantId).stream()
                .filter(s -> s.isActive() && !isTerminal(s.getCode()))
                .map(PipelineStage::getCode)
                .findFirst()
                .orElse(DEFAULTS.get(0)[0]);
    }

    /** All stages (active and not) in pipeline order — for summaries and labels. */
    @Transactional
    public List<PipelineStage> stages(Long tenantId) {
        ensureDefaults(tenantId);
        return stageRepository.findByTenantIdOrderBySortOrderAscLabelAsc(tenantId);
    }

    // ==================== Helpers ====================

    private void ensureDefaults(Long tenantId) {
        if (stageRepository.existsByTenantId(tenantId)) {
            return;
        }
        for (String[] stage : DEFAULTS) {
            stageRepository.insertIfAbsent(tenantId, stage[0], stage[1], Integer.parseInt(stage[2]));
        }
    }

    private static String cleanLabel(String raw) {
        String label = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (label.isEmpty()) {
            throw ApiException.badRequest("Enter a stage name");
        }
        if (label.length() > 60) {
            throw ApiException.badRequest("Stage name must be at most 60 characters");
        }
        return label;
    }

    /** "Site Visit" → site_visit (+ _2, _3… if taken), max 30 chars; never 'won'/'lost'/'order' (a route). */
    private static String uniqueCode(String label, List<PipelineStage> existing) {
        String base = Normalizer.normalize(label, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "stage";
        }
        base = base.length() > 26 ? base.substring(0, 26) : base;
        String code = base;
        for (int n = 2; isTerminal(code) || "order".equals(code) || codeTaken(code, existing); n++) {
            code = base + "_" + n;
        }
        return code;
    }

    private static boolean codeTaken(String code, List<PipelineStage> existing) {
        return existing.stream().anyMatch(s -> s.getCode().equals(code));
    }

    private PipelineStageResDto toDto(PipelineStage s) {
        return new PipelineStageResDto(s.getCode(), s.getLabel(), s.getSortOrder(), s.isActive(), isTerminal(s.getCode()));
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Sales stages are configured per tenant");
        }
        return currentUser.getTenant().getId();
    }
}
