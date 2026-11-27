package com.company.crm.meeting.event;

/** FR-5.5: a meeting was scheduled, changed or cancelled — invites go out after commit. */
public record MeetingChangedEvent(Long tenantId, Long meetingId, Kind kind) {

    public enum Kind { SCHEDULED, UPDATED, CANCELLED }
}
