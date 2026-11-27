package com.company.crm.meeting.service;

import com.company.crm.common.mail.MailService;
import com.company.crm.communication.service.CommunicationService;
import com.company.crm.meeting.entity.Meeting;
import com.company.crm.meeting.repository.MeetingRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-5.5b: sends a meeting's optional reminder once its time comes — to the organizer, and to the
 * customer/lead contact when they are included (logged on the timeline). Once per start time;
 * the claim makes concurrent runs safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingReminderService {

    private final MeetingRepository meetingRepository;
    private final MailService mailService;
    private final CommunicationService communicationService;

    @Transactional(readOnly = true)
    public List<Long> findDue() {
        return meetingRepository.findDueReminderIds(LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remind(Long meetingId) {
        if (meetingRepository.claimReminder(meetingId, LocalDateTime.now()) == 0) {
            return;
        }
        Meeting m = meetingRepository.findById(meetingId).orElse(null);
        if (m == null || m.isCancelled()) {
            return;
        }
        String subject = "Reminder: " + m.getTitle() + " - " + MeetingMessages.when(m);
        String body = MeetingMessages.body(m, "Reminder: you have an upcoming meeting.");
        try {
            mailService.sendCalendarEmail(m.getOrganizer().getEmail(), subject, body, null);
        } catch (MailException | MessagingException e) {
            log.warn("Meeting {} reminder to organizer failed: {}", meetingId, e.getMessage());
        }
        if (m.isIncludeCustomer()) {
            communicationService.sendAutomatedEmail(m.getTenant(), m.getCustomer(), m.getLead(),
                    MeetingService.contactEmail(m), subject, body, null);
        }
    }
}
