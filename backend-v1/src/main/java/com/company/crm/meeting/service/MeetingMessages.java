package com.company.crm.meeting.service;

import com.company.crm.meeting.entity.Meeting;

import java.time.format.DateTimeFormatter;

/** FR-5.5: the plain-text wording of meeting invites, changes, cancellations and reminders. */
final class MeetingMessages {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private MeetingMessages() {
    }

    static String when(Meeting m) {
        return WHEN.format(m.getStartsAt()) + " - " + TIME.format(m.getEndsAt());
    }

    static String body(Meeting m, String intro) {
        StringBuilder b = new StringBuilder(intro).append("\n\n");
        b.append(m.getTitle()).append('\n');
        b.append("When: ").append(when(m)).append('\n');
        if (m.getLocation() != null) {
            b.append("Where: ").append(m.getLocation()).append('\n');
        }
        String with = m.getCustomer() != null ? m.getCustomer().getCompanyName()
                : m.getLead().getLeadName() + " (" + m.getLead().getCompanyName() + ")";
        b.append("With: ").append(with).append('\n');
        b.append("Organizer: ").append(m.getOrganizer().getFullName()).append(" (").append(m.getTenant().getCompanyName()).append(")\n");
        if (m.getNotes() != null) {
            b.append('\n').append(m.getNotes()).append('\n');
        }
        return b.toString();
    }
}
