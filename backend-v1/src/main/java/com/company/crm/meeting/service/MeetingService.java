package com.company.crm.meeting.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerContact;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.lead.entity.Lead;
import com.company.crm.lead.repository.LeadRepository;
import com.company.crm.meeting.dto.MeetingReqDto;
import com.company.crm.meeting.dto.MeetingResDto;
import com.company.crm.meeting.entity.Meeting;
import com.company.crm.meeting.event.MeetingChangedEvent;
import com.company.crm.meeting.repository.MeetingRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FR-5.5: meetings with customers and leads. (a) schedule, reschedule and cancel; (b) an optional
 * reminder (see MeetingReminderService); (c) iCalendar — each meeting downloads as .ics, invites
 * carry one, and every user has a private feed URL their calendar app can subscribe to.
 * Access follows the customer/lead: whoever may see the record may see and manage its meetings.
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    /** How far back the calendar feed reaches. */
    private static final int FEED_DAYS_BACK = 60;

    private final MeetingRepository meetingRepository;
    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final DataScopeResolver dataScopeResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<MeetingResDto> list(User currentUser, Long customerId, Long leadId) {
        Long tenantId = requireTenantId(currentUser);
        if (customerId != null) {
            resolveCustomer(currentUser, customerId);
            return meetingRepository.findByTenantIdAndCustomerIdOrderByStartsAtDesc(tenantId, customerId).stream().map(this::toDto).toList();
        }
        if (leadId != null) {
            resolveLead(currentUser, leadId);
            return meetingRepository.findByTenantIdAndLeadIdOrderByStartsAtDesc(tenantId, leadId).stream().map(this::toDto).toList();
        }
        // My agenda: the caller's upcoming meetings.
        return meetingRepository.findByOrganizerIdAndStatusAndEndsAtAfterOrderByStartsAtAsc(
                currentUser.getId(), Meeting.SCHEDULED, LocalDateTime.now()).stream().map(this::toDto).toList();
    }

    @Transactional
    public MeetingResDto schedule(User currentUser, MeetingReqDto dto) {
        if ((dto.getCustomerId() == null) == (dto.getLeadId() == null)) {
            throw ApiException.badRequest("Choose the customer or the lead this meeting is with");
        }
        validateTimes(dto);
        if (dto.getStartsAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw ApiException.badRequest("A meeting can't be scheduled in the past");
        }
        Meeting meeting = new Meeting();
        meeting.setTenant(currentUser.getTenant());
        if (dto.getCustomerId() != null) {
            meeting.setCustomer(resolveCustomer(currentUser, dto.getCustomerId()));
        } else {
            meeting.setLead(resolveLead(currentUser, dto.getLeadId()));
        }
        meeting.setOrganizer(userRepository.findById(currentUser.getId()).orElseThrow());
        meeting.setCreatedBy(meeting.getOrganizer());
        meeting.setIcalUid(UUID.randomUUID() + "@smartcrm");
        applyFields(meeting, dto);
        Meeting saved = meetingRepository.save(meeting);
        eventPublisher.publishEvent(new MeetingChangedEvent(saved.getTenant().getId(), saved.getId(), MeetingChangedEvent.Kind.SCHEDULED));
        return toDto(saved);
    }

    /** Reschedule/edit (the customer or lead stays the same). A new time re-arms the reminder. */
    @Transactional
    public MeetingResDto update(User currentUser, Long meetingId, MeetingReqDto dto) {
        Meeting meeting = findAccessible(currentUser, meetingId);
        if (meeting.isCancelled()) {
            throw ApiException.badRequest("A cancelled meeting can't be changed");
        }
        validateTimes(dto);
        boolean timeChanged = !meeting.getStartsAt().equals(dto.getStartsAt())
                || !Objects.equals(meeting.getReminderMinutes(), dto.getReminderMinutes());
        applyFields(meeting, dto);
        if (timeChanged) {
            meeting.setReminderSentAt(null);
        }
        meeting.setIcalSequence(meeting.getIcalSequence() + 1);
        Meeting saved = meetingRepository.save(meeting);
        eventPublisher.publishEvent(new MeetingChangedEvent(saved.getTenant().getId(), saved.getId(), MeetingChangedEvent.Kind.UPDATED));
        return toDto(saved);
    }

    @Transactional
    public MeetingResDto cancel(User currentUser, Long meetingId) {
        Meeting meeting = findAccessible(currentUser, meetingId);
        if (!meeting.isCancelled()) {
            meeting.setStatus(Meeting.CANCELLED);
            meeting.setIcalSequence(meeting.getIcalSequence() + 1);
            meetingRepository.save(meeting);
            eventPublisher.publishEvent(new MeetingChangedEvent(meeting.getTenant().getId(), meeting.getId(), MeetingChangedEvent.Kind.CANCELLED));
        }
        return toDto(meeting);
    }

    /** FR-5.5c: one meeting as an .ics file (adds it to any calendar app). */
    @Transactional(readOnly = true)
    public String ics(User currentUser, Long meetingId) {
        Meeting meeting = findAccessible(currentUser, meetingId);
        return IcsCalendar.of(meeting.getTitle(), IcsCalendar.PUBLISH, List.of(meeting), this::attendeeEmail);
    }

    /** FR-5.5c: the caller's private feed token (created on first use). */
    @Transactional
    public String feedToken(User currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow();
        if (user.getCalendarFeedToken() == null) {
            user.setCalendarFeedToken(newToken());
            userRepository.save(user);
        }
        return user.getCalendarFeedToken();
    }

    /** FR-5.5c: replaces the feed token — the old link stops working (e.g. after it leaked). */
    @Transactional
    public String resetFeedToken(User currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow();
        user.setCalendarFeedToken(newToken());
        userRepository.save(user);
        return user.getCalendarFeedToken();
    }

    /** FR-5.5c: the feed behind a token — the user's meetings from 60 days ago on. 404 for an unknown token. */
    @Transactional(readOnly = true)
    public String feed(String token) {
        User user = (token == null || token.isBlank() ? null : userRepository.findByCalendarFeedToken(token).orElse(null));
        if (user == null) {
            throw ApiException.notFound("Calendar not found");
        }
        List<Meeting> meetings = meetingRepository.findByOrganizerIdAndStartsAtAfterOrderByStartsAtAsc(
                user.getId(), LocalDateTime.now().minusDays(FEED_DAYS_BACK));
        return IcsCalendar.of("SmartCRM meetings - " + user.getFullName(), IcsCalendar.PUBLISH, meetings, this::attendeeEmail);
    }

    /** The customer/lead contact's email when they are included in the meeting, else null. */
    public String attendeeEmail(Meeting meeting) {
        return meeting.isIncludeCustomer() ? contactEmail(meeting) : null;
    }

    /** The customer's email (or first contact's), or the lead's contact email. */
    public static String contactEmail(Meeting meeting) {
        if (meeting.getCustomer() != null) {
            Customer c = meeting.getCustomer();
            if (c.getEmail() != null && !c.getEmail().isBlank()) {
                return c.getEmail();
            }
            return c.getContacts().stream().map(CustomerContact::getEmail).filter(e -> e != null && !e.isBlank()).findFirst().orElse(null);
        }
        Lead lead = meeting.getLead();
        return lead != null && lead.getContactEmail() != null && !lead.getContactEmail().isBlank() ? lead.getContactEmail() : null;
    }

    // ==================== Helpers ====================

    private void applyFields(Meeting meeting, MeetingReqDto dto) {
        meeting.setTitle(dto.getTitle().trim());
        meeting.setStartsAt(dto.getStartsAt());
        meeting.setEndsAt(dto.getEndsAt());
        meeting.setLocation(blankToNull(dto.getLocation()));
        meeting.setNotes(blankToNull(dto.getNotes()));
        meeting.setReminderMinutes(dto.getReminderMinutes());
        meeting.setIncludeCustomer(dto.isIncludeCustomer());
    }

    private static void validateTimes(MeetingReqDto dto) {
        if (!dto.getEndsAt().isAfter(dto.getStartsAt())) {
            throw ApiException.badRequest("The meeting must end after it starts");
        }
    }

    private Meeting findAccessible(User currentUser, Long meetingId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Meeting not found"));
        if (meeting.getCustomer() != null) {
            assertAccess(currentUser, meeting.getCustomer().getOwner(), "customer");
        } else {
            assertAccess(currentUser, meeting.getLead().getOwner(), "lead");
        }
        return meeting;
    }

    private Customer resolveCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        assertAccess(currentUser, customer.getOwner(), "customer");
        return customer;
    }

    private Lead resolveLead(User currentUser, Long leadId) {
        Lead lead = leadRepository.findByIdAndTenantId(leadId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Lead not found"));
        assertAccess(currentUser, lead.getOwner(), "lead");
        return lead;
    }

    private void assertAccess(User currentUser, User owner, String label) {
        dataScopeResolver.assertCanAccess(currentUser, owner != null ? owner.getId() : null, label);
    }

    private MeetingResDto toDto(Meeting m) {
        return new MeetingResDto(m.getId(),
                m.getCustomer() != null ? m.getCustomer().getId() : null,
                m.getCustomer() != null ? m.getCustomer().getCompanyName() : null,
                m.getLead() != null ? m.getLead().getId() : null,
                m.getLead() != null ? m.getLead().getLeadName() : null,
                m.getTitle(), m.getStartsAt(), m.getEndsAt(), m.getLocation(), m.getNotes(),
                m.getOrganizer().getId(), m.getOrganizer().getFullName(), m.getStatus(),
                m.getReminderMinutes(), m.isIncludeCustomer(), m.getReminderSentAt());
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Meetings are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
