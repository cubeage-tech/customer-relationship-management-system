package com.company.crm.notification.event;

/**
 * FR-M.4: a lead or opportunity was given to someone other than the person who did it. Published
 * inside the assigning transaction; the notification is created only once it commits.
 *
 * @param entityType lead | opportunity
 */
public record RecordAssignedEvent(Long tenantId, Long assigneeId, Long assignedById, String assignedByName,
                                  String entityType, Long entityId, String title, String detail) {}
