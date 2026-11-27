package com.company.crm.meeting.service;

import com.company.crm.common.mail.MailService;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.meeting.entity.Meeting;
import com.company.crm.meeting.event.MeetingChangedEvent;
import com.company.crm.meeting.repository.MeetingRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * FR-5.5/5.5c: after a meeting is scheduled, changed or cancelled (and the change committed), email
 * the organizer — and the customer/lead contact when included — with an iCalendar invite or
 * cancellation, so it lands in their calendar. Customer emails are logged on the timeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingNotifier {

    private final MeetingRepository meetingRepository;
    private final MeetingService meetingService;
    private final MailService mailService;
    private final CommunicationService communicationService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMeetingChanged(MeetingChangedEvent event) {
        Meeting m = meetingRepository.findByIdAndTenantId(event.meetingId(), event.tenantId()).orElse(null);
        if (m == null) {
            return;
        }
        boolean cancelled = event.kind() == MeetingChangedEvent.Kind.CANCELLED;
        String ics = IcsCalendar.of(m.getTitle(), cancelled ? IcsCalendar.CANCEL : IcsCalendar.REQUEST, List.of(m), meetingService::attendeeEmail);
        String prefix = switch (event.kind()) {
            case SCHEDULED -> "Invitation";
            case UPDATED -> "Updated";
            case CANCELLED -> "Cancelled";
        };
        String subject = prefix + ": " + m.getTitle() + " - " + MeetingMessages.when(m);
        String intro = switch (event.kind()) {
            case SCHEDULED -> "A meeting has been scheduled.";
            case UPDATED -> "This meeting has been updated.";
            case CANCELLED -> "This meeting has been cancelled.";
        };
        String body = MeetingMessages.body(m, intro);

        try {
            mailService.sendCalendarEmail(m.getOrganizer().getEmail(), subject, body, ics);
        } catch (MailException | MessagingException e) {
            log.warn("Meeting {} email to organizer failed: {}", m.getId(), e.getMessage());
        }
        if (m.isIncludeCustomer()) {
            communicationService.sendAutomatedEmail(m.getTenant(), m.getCustomer(), m.getLead(),
                    MeetingService.contactEmail(m), subject, body, ics);
        }
    }
}
