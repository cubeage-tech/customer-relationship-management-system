package com.company.crm.campaign.service;

import com.company.crm.campaign.dto.request.SegmentReqDto;
import com.company.crm.campaign.dto.response.SegmentResDto;
import com.company.crm.campaign.entity.CustomerSegment;
import com.company.crm.campaign.repository.CustomerSegmentQuery;
import com.company.crm.campaign.repository.CustomerSegmentRepository;
import com.company.crm.common.enums.LeadSource;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FR-7.2: customer segments for marketing — saved criteria over industry, purchase history,
 * engagement and lead source, evaluated live whenever they are previewed or a campaign sends.
 */
@Service
@RequiredArgsConstructor
public class SegmentService {

    /** Most members a preview returns (the count is always exact). */
    private static final int PREVIEW_LIMIT = 200;

    private final CustomerSegmentRepository segmentRepository;
    private final CustomerSegmentQuery segmentQuery;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<SegmentResDto> list(User currentUser) {
        LocalDateTime now = LocalDateTime.now();
        return segmentRepository.findByTenantIdOrderByNameAsc(requireTenantId(currentUser)).stream()
                .map(s -> toDto(s, segmentQuery.customerIds(s, now).size()))
                .toList();
    }

    @Transactional
    public SegmentResDto create(User currentUser, SegmentReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        if (segmentRepository.existsByTenantIdAndNameIgnoreCase(tenantId, dto.getName().trim())) {
            throw ApiException.conflict("A segment with this name already exists");
        }
        CustomerSegment segment = new CustomerSegment();
        segment.setTenant(currentUser.getTenant());
        segment.setCreatedBy(currentUser);
        applyFields(segment, dto);
        CustomerSegment saved = segmentRepository.save(segment);
        return toDto(saved, segmentQuery.customerIds(saved, LocalDateTime.now()).size());
    }

    @Transactional
    public SegmentResDto update(User currentUser, Long segmentId, SegmentReqDto dto) {
        CustomerSegment segment = find(currentUser, segmentId);
        if (segmentRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(segment.getTenant().getId(), dto.getName().trim(), segmentId)) {
            throw ApiException.conflict("A segment with this name already exists");
        }
        applyFields(segment, dto);
        CustomerSegment saved = segmentRepository.save(segment);
        return toDto(saved, segmentQuery.customerIds(saved, LocalDateTime.now()).size());
    }

    /** Campaigns that used it keep their recipients; they just lose the link (ON DELETE SET NULL). */
    @Transactional
    public void delete(User currentUser, Long segmentId) {
        segmentRepository.delete(find(currentUser, segmentId));
    }

    /** FR-7.2: who the segment matches now (first {@value #PREVIEW_LIMIT}). */
    @Transactional(readOnly = true)
    public List<SegmentResDto.Member> members(User currentUser, Long segmentId) {
        return membersOf(find(currentUser, segmentId), PREVIEW_LIMIT);
    }

    /** All matching customers, in company-name order. */
    @Transactional(readOnly = true)
    public List<Customer> customers(CustomerSegment segment) {
        List<Long> ids = segmentQuery.customerIds(segment, LocalDateTime.now());
        Map<Long, Customer> byId = customerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(c -> c != null).toList();
    }

    @Transactional(readOnly = true)
    public CustomerSegment requireSegment(User currentUser, Long segmentId) {
        return find(currentUser, segmentId);
    }

    // ==================== Helpers ====================

    private List<SegmentResDto.Member> membersOf(CustomerSegment segment, int limit) {
        return customers(segment).stream().limit(limit)
                .map(c -> new SegmentResDto.Member(c.getId(), c.getCompanyName(), c.getIndustry(), c.getEmail(), c.getPhone(),
                        c.isMarketingOptOut()))
                .toList();
    }

    private void applyFields(CustomerSegment s, SegmentReqDto dto) {
        s.setName(dto.getName().trim());
        s.setDescription(blankToNull(dto.getDescription()));
        List<String> industries = dto.getIndustries() == null ? List.of()
                : dto.getIndustries().stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
        if (industries.stream().anyMatch(v -> !v.matches("[a-z0-9_]{1,30}"))) {
            throw ApiException.badRequest("Unknown industry in the segment");
        }
        s.setIndustries(industries.isEmpty() ? null : String.join(",", industries));
        if (dto.getLeadSource() != null && !dto.getLeadSource().isBlank()) {
            try {
                s.setLeadSource(LeadSource.fromDbValue(dto.getLeadSource().trim()).getDbValue());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown lead source: " + dto.getLeadSource());
            }
        } else {
            s.setLeadSource(null);
        }
        if (dto.getPurchasedWithinDays() != null && dto.getNotPurchasedWithinDays() != null
                && dto.getNotPurchasedWithinDays() <= dto.getPurchasedWithinDays()) {
            throw ApiException.badRequest("'Bought within' and 'not bought within' contradict each other");
        }
        if (dto.getContactedWithinDays() != null && dto.getNotContactedWithinDays() != null
                && dto.getNotContactedWithinDays() <= dto.getContactedWithinDays()) {
            throw ApiException.badRequest("'Contacted within' and 'not contacted within' contradict each other");
        }
        s.setPurchasedWithinDays(dto.getPurchasedWithinDays());
        s.setNotPurchasedWithinDays(dto.getNotPurchasedWithinDays());
        s.setMinPurchaseValue(dto.getMinPurchaseValue());
        s.setContactedWithinDays(dto.getContactedWithinDays());
        s.setNotContactedWithinDays(dto.getNotContactedWithinDays());
    }

    private SegmentResDto toDto(CustomerSegment s, long count) {
        return new SegmentResDto(s.getId(), s.getName(), s.getDescription(), CustomerSegmentQuery.industries(s.getIndustries()),
                s.getLeadSource(), s.getPurchasedWithinDays(), s.getNotPurchasedWithinDays(), s.getMinPurchaseValue(),
                s.getContactedWithinDays(), s.getNotContactedWithinDays(), count);
    }

    private CustomerSegment find(User currentUser, Long segmentId) {
        return segmentRepository.findByIdAndTenantId(segmentId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Segment not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Segments are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
