package com.company.crm.meeting.service;

import com.company.crm.meeting.entity.Meeting;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FR-5.5c: writes iCalendar (RFC 5545) text for meetings — invites (METHOD:REQUEST/CANCEL), single
 * downloads and subscribable feeds (METHOD:PUBLISH). Meeting times are stored in the application's
 * time zone and written in UTC, so every calendar app shows them at the right local time.
 */
public final class IcsCalendar {

    public static final String PUBLISH = "PUBLISH";
    public static final String REQUEST = "REQUEST";
    public static final String CANCEL = "CANCEL";

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private IcsCalendar() {
    }

    /** @param attendees the customer/lead contact invited to each meeting (null email = none) */
    public static String of(String calendarName, String method, List<Meeting> meetings, AttendeeLookup attendees) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//SmartCRM AI//Meetings//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:" + method);
        line(out, "X-WR-CALNAME:" + text(calendarName));
        for (Meeting m : meetings) {
            event(out, m, method, attendees.emailOf(m));
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** Who (besides the organizer) is invited — the customer/lead contact when included. */
    @FunctionalInterface
    public interface AttendeeLookup {
        String emailOf(Meeting meeting);
    }

    private static void event(StringBuilder out, Meeting m, String method, String attendeeEmail) {
        boolean cancelled = m.isCancelled() || CANCEL.equals(method);
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + m.getIcalUid());
        line(out, "SEQUENCE:" + m.getIcalSequence());
        line(out, "DTSTAMP:" + UTC.format(Instant.now()));
        line(out, "DTSTART:" + utc(m.getStartsAt()));
        line(out, "DTEND:" + utc(m.getEndsAt()));
        line(out, "SUMMARY:" + text(m.getTitle()));
        if (m.getLocation() != null && !m.getLocation().isBlank()) {
            line(out, "LOCATION:" + text(m.getLocation()));
        }
        String with = m.getCustomer() != null ? m.getCustomer().getCompanyName()
                : m.getLead() != null ? m.getLead().getLeadName() + " (" + m.getLead().getCompanyName() + ")" : null;
        String description = (with != null ? "With " + with : "") + (m.getNotes() != null && !m.getNotes().isBlank()
                ? (with != null ? "\n\n" : "") + m.getNotes() : "");
        if (!description.isEmpty()) {
            line(out, "DESCRIPTION:" + text(description));
        }
        line(out, "ORGANIZER;CN=" + param(m.getOrganizer().getFullName()) + ":mailto:" + m.getOrganizer().getEmail());
        if (attendeeEmail != null) {
            line(out, "ATTENDEE;ROLE=REQ-PARTICIPANT;RSVP=FALSE:mailto:" + attendeeEmail);
        }
        line(out, "STATUS:" + (cancelled ? "CANCELLED" : "CONFIRMED"));
        if (!cancelled && m.getReminderMinutes() != null) {
            line(out, "BEGIN:VALARM");
            line(out, "ACTION:DISPLAY");
            line(out, "DESCRIPTION:" + text(m.getTitle()));
            line(out, "TRIGGER:-PT" + m.getReminderMinutes() + "M");
            line(out, "END:VALARM");
        }
        line(out, "END:VEVENT");
    }

    private static String utc(LocalDateTime local) {
        return UTC.format(local.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** TEXT value escaping (RFC 5545 §3.3.11). */
    static String text(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Parameter values can't contain quotes, colons or semicolons unquoted. */
    private static String param(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "'")) + "\"";
    }

    /** Appends a content line folded at 75 octets, CRLF-terminated (RFC 5545 §3.1). */
    private static void line(StringBuilder out, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            out.append(content).append("\r\n");
            return;
        }
        StringBuilder current = new StringBuilder();
        int octets = 0;
        int limit = 75;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int len = ch.getBytes(StandardCharsets.UTF_8).length;
            if (octets + len > limit) {
                out.append(current).append("\r\n ");
                current.setLength(0);
                octets = 0;
                limit = 74; // continuation lines start with a space
            }
            current.append(ch);
            octets += len;
            i += Character.charCount(cp);
        }
        out.append(current).append("\r\n");
    }
}
