package com.company.crm.analytics.service;

import com.company.crm.analytics.dto.AnalyticsDtos.ScheduleReq;
import com.company.crm.analytics.dto.AnalyticsDtos.ScheduleRes;
import com.company.crm.analytics.entity.ReportSchedule;
import com.company.crm.analytics.export.ReportRunner;
import com.company.crm.analytics.export.ReportRunner.Filters;
import com.company.crm.analytics.export.ReportRunner.Format;
import com.company.crm.analytics.export.ReportRunner.Rendered;
import com.company.crm.analytics.repository.ReportScheduleRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.mail.MailService;
import com.company.crm.salesforce.service.SalesForceScope;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REP-SCH: reports generated on a schedule and emailed as PDF or XLSX. A schedule always runs as
 * its owner, through the same role check and data scope as viewing the report (REP-SCOPE), and
 * only goes to users of the same company. Each run is claimed first, so concurrent jobs send once.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportScheduleService {

    private static final Set<String> FREQUENCIES = Set.of("daily", "weekly", "monthly");

    private final ReportScheduleRepository repository;
    private final ReportRunner runner;
    private final ReportScopeResolver scopeResolver;
    private final SalesForceScope salesForceScope;
    private final UserRepository userRepository;
    private final MailService mailService;

    // ==================== Management ====================

    /** Admins see every schedule in the company; everyone else their own. */
    @Transactional(readOnly = true)
    public List<ScheduleRes> list(User user) {
        Long tenantId = salesForceScope.tenantId(user);
        List<ReportSchedule> schedules = isAdmin(user)
                ? repository.findByTenantIdOrderByNameAsc(tenantId)
                : repository.findByTenantIdAndOwnerIdOrderByNameAsc(tenantId, user.getId());
        Map<Long, String> owners = userRepository.findByTenantIdAndIdIn(tenantId,
                        schedules.stream().map(ReportSchedule::getOwnerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return schedules.stream().map(s -> toRes(s, owners.get(s.getOwnerId()))).toList();
    }

    @Transactional
    public ScheduleRes create(User user, ScheduleReq dto) {
        ReportSchedule s = new ReportSchedule();
        s.setTenantId(salesForceScope.tenantId(user));
        s.setOwnerId(user.getId());
        apply(user, s, dto);
        return toRes(repository.save(s), user.getFullName());
    }

    /** The owner (or an admin) may change a schedule; it keeps running as its owner. */
    @Transactional
    public ScheduleRes update(User user, Long id, ScheduleReq dto) {
        ReportSchedule s = find(user, id);
        User owner = owner(s);
        apply(owner, s, dto);
        return toRes(repository.save(s), owner.getFullName());
    }

    @Transactional
    public void delete(User user, Long id) {
        repository.delete(find(user, id));
    }

    /**
     * Generates and emails the report now, without moving the schedule. Not one transaction: the
     * report runs in its own read-only transaction, so a failure there is recorded, not rolled back.
     */
    public ScheduleRes sendNow(User user, Long id) {
        ReportSchedule s = find(user, id);
        deliver(s, LocalDateTime.now());
        return toRes(repository.save(s), owner(s).getFullName());
    }

    // ==================== The job ====================

    @Transactional(readOnly = true)
    public List<Long> findDue() {
        return repository.findDueIds(LocalDateTime.now());
    }

    /**
     * Runs one due schedule: claim (its own transaction), generate and email, record the outcome.
     * Each step commits separately, so one schedule's failure is recorded and doesn't block the rest.
     */
    public void runDue(Long id) {
        ReportSchedule s = repository.findById(id).orElse(null);
        if (s == null || !s.isActive()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = s.getNextRunAt();
        if (repository.claimRun(id, due, nextRun(s.getFrequency(), s.getHourOfDay(), now), now) == 0) {
            return; // another run took it
        }
        s = repository.findById(id).orElseThrow();
        deliver(s, due);
        repository.save(s);
    }

    /** Builds the report as the owner for the period ending before {@code runAt}, and emails it. */
    private void deliver(ReportSchedule s, LocalDateTime runAt) {
        LocalDate[] period = period(s.getFrequency(), runAt.toLocalDate());
        try {
            User owner = owner(s);
            if (owner.getStatus() != AccountStatus.ACTIVE) {
                throw ApiException.forbidden("The schedule's owner account is not active");
            }
            ReportType type = ReportType.fromCode(s.getReportType());
            Rendered file = runner.render(owner, type,
                    new Filters(period[0], period[1], s.getIndustry(), s.getTeamId(), s.getUserId(), null, null),
                    Format.fromCode(s.getFormat()));
            mailService.sendReportEmail(recipients(s).toArray(String[]::new), s.getName() + " — " + period[0] + " to " + period[1],
                    "Hello,\n\nAttached is the " + type.getTitle().toLowerCase() + " \"" + s.getName() + "\" for " + period[0]
                            + " to " + period[1] + ", generated for " + owner.getFullName() + ".\n\nThis email was sent automatically by SmartCRM.",
                    file.filename(), file.bytes(), file.contentType());
            s.setLastStatus("sent");
            s.setLastError(null);
        } catch (RuntimeException | MessagingException e) { // access lost, bad filter, or mail failure: recorded on the schedule
            log.warn("Scheduled report {} failed: {}", s.getId(), e.getMessage());
            s.setLastStatus("failed");
            s.setLastError(truncate(e.getMessage()));
        }
        s.setLastRunAt(LocalDateTime.now());
    }

    // ==================== Rules ====================

    /** daily = yesterday; weekly = the 7 days before; monthly = the previous calendar month. */
    static LocalDate[] period(String frequency, LocalDate runDate) {
        return switch (frequency) {
            case "weekly" -> new LocalDate[]{runDate.minusDays(7), runDate.minusDays(1)};
            case "monthly" -> {
                LocalDate first = runDate.minusMonths(1).withDayOfMonth(1);
                yield new LocalDate[]{first, first.with(TemporalAdjusters.lastDayOfMonth())};
            }
            default -> new LocalDate[]{runDate.minusDays(1), runDate.minusDays(1)};
        };
    }

    /** The next run strictly after {@code after}: daily at the hour, Mondays, or the 1st of the month. */
    static LocalDateTime nextRun(String frequency, int hour, LocalDateTime after) {
        LocalDate day = after.toLocalDate();
        while (true) {
            LocalDate candidate = switch (frequency) {
                case "weekly" -> day.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
                case "monthly" -> day.getDayOfMonth() == 1 ? day : day.with(TemporalAdjusters.firstDayOfNextMonth());
                default -> day;
            };
            LocalDateTime at = candidate.atTime(hour, 0);
            if (at.isAfter(after)) {
                return at;
            }
            day = candidate.plusDays(1);
        }
    }

    private void apply(User owner, ReportSchedule s, ScheduleReq dto) {
        ReportType type = ReportType.fromCode(dto.getReportType());
        if (!type.allows(owner.getRole().getName())) {
            throw ApiException.forbidden("Your role can't run the " + type.getTitle().toLowerCase());
        }
        Format format = Format.fromCode(dto.getFormat());
        if (!FREQUENCIES.contains(dto.getFrequency())) {
            throw ApiException.badRequest("Frequency must be daily, weekly or monthly");
        }
        boolean salesFilters = type != ReportType.SERVICE && type != ReportType.CAMPAIGNS;
        String industry = salesFilters && dto.getIndustry() != null && !dto.getIndustry().isBlank() ? dto.getIndustry().trim() : null;
        Long teamId = salesFilters ? dto.getTeamId() : null;
        Long userId = salesFilters ? dto.getUserId() : null;
        scopeResolver.resolve(owner, null, null, industry, teamId, userId); // the filters must be within the owner's scope

        int hour = dto.getHourOfDay() != null ? dto.getHourOfDay() : 7;
        boolean timingChanged = s.getId() == null || !dto.getFrequency().equals(s.getFrequency()) || hour != s.getHourOfDay();
        s.setName(dto.getName().trim());
        s.setReportType(type.getCode());
        s.setFormat(format.extension);
        s.setFrequency(dto.getFrequency());
        s.setHourOfDay(hour);
        s.setRecipients(String.join(",", validRecipients(owner, dto.getRecipients())));
        s.setIndustry(industry);
        s.setTeamId(teamId);
        s.setUserId(userId);
        boolean reactivated = dto.getActive() != null && dto.getActive() && !s.isActive();
        if (dto.getActive() != null) {
            s.setActive(dto.getActive());
        }
        if (timingChanged || reactivated) {
            s.setNextRunAt(nextRun(s.getFrequency(), hour, LocalDateTime.now()));
        }
    }

    /** Recipients must be active users of the same company (the owner by default). */
    private List<String> validRecipients(User owner, List<String> requested) {
        List<String> emails = requested == null ? List.of() : requested.stream()
                .filter(Objects::nonNull).map(e -> e.trim().toLowerCase()).filter(e -> !e.isEmpty()).distinct().toList();
        if (emails.isEmpty()) {
            return List.of(owner.getEmail());
        }
        Set<String> allowed = userRepository.findByTenantId(owner.getTenant().getId()).stream()
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                .map(u -> u.getEmail().toLowerCase()).collect(Collectors.toSet());
        List<String> outside = emails.stream().filter(e -> !allowed.contains(e)).toList();
        if (!outside.isEmpty()) {
            throw ApiException.badRequest("Reports can only be emailed to active users of your company: " + String.join(", ", outside));
        }
        return emails;
    }

    private ReportSchedule find(User user, Long id) {
        ReportSchedule s = repository.findById(id)
                .filter(r -> r.getTenantId().equals(salesForceScope.tenantId(user)))
                .orElseThrow(() -> ApiException.notFound("Report schedule not found"));
        if (!s.getOwnerId().equals(user.getId()) && !isAdmin(user)) {
            throw ApiException.forbidden("Only the schedule's owner or an admin can change it");
        }
        return s;
    }

    private User owner(ReportSchedule s) {
        return userRepository.findById(s.getOwnerId()).orElseThrow(() -> ApiException.notFound("Schedule owner not found"));
    }

    private List<String> recipients(ReportSchedule s) {
        return Arrays.stream(s.getRecipients().split(",")).filter(e -> !e.isBlank()).toList();
    }

    private static boolean isAdmin(User user) {
        return user.getRole().getName() == RoleType.ADMIN;
    }

    private static String truncate(String v) {
        return v == null ? null : v.length() > 500 ? v.substring(0, 500) : v;
    }

    private ScheduleRes toRes(ReportSchedule s, String ownerName) {
        ReportType type = ReportType.fromCode(s.getReportType());
        return new ScheduleRes(s.getId(), s.getName(), s.getReportType(), type.getTitle(), s.getFormat(), s.getFrequency(),
                s.getHourOfDay(), recipients(s), s.getIndustry(), s.getTeamId(), s.getUserId(), s.isActive(), s.getOwnerId(),
                ownerName, s.getNextRunAt(), s.getLastRunAt(), s.getLastStatus(), s.getLastError());
    }
}
