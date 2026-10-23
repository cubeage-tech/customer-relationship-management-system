package com.company.crm.meeting.dto;

import java.time.LocalDateTime;

/** FR-5.5: a meeting as shown on customer/lead pages and the organizer's agenda. */
public record MeetingResDto(Long id, Long customerId, String customerName, Long leadId, String leadName,
                            String title, LocalDateTime startsAt, LocalDateTime endsAt, String location, String notes,
                            Long organizerId, String organizerName, String status,
                            Integer reminderMinutes, boolean includeCustomer, LocalDateTime reminderSentAt) {}
