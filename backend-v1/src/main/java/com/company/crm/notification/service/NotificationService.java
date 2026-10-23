package com.company.crm.notification.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.notification.dto.NotificationDtos.DeviceReq;
import com.company.crm.notification.dto.NotificationDtos.Inbox;
import com.company.crm.notification.dto.NotificationDtos.MessageReq;
import com.company.crm.notification.dto.NotificationDtos.MessageRes;
import com.company.crm.notification.dto.NotificationDtos.NotificationRes;
import com.company.crm.notification.entity.Notification;
import com.company.crm.notification.entity.UserDevice;
import com.company.crm.notification.event.NotificationCreatedEvent;
import com.company.crm.notification.event.RecordAssignedEvent;
import com.company.crm.notification.repository.NotificationRepository;
import com.company.crm.notification.repository.UserDeviceRepository;
import com.company.crm.salesforce.service.SalesForceScope;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FR-M.4: notifications for assignments, follow-ups and manager messages — stored as an in-app
 * inbox and pushed to the user's registered devices (see PushDispatcher).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int INBOX_SIZE = 100;
    private static final Set<String> PLATFORMS = Set.of("android", "ios");

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final SalesForceScope salesForceScope;
    private final ApplicationEventPublisher eventPublisher;

    /** Stores a notification and queues its push for after the surrounding transaction commits. */
    @Transactional
    public Notification notify(Long tenantId, Long userId, String type, String title, String body,
                               String entityType, Long entityId, Long senderId) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(truncate(title, 200));
        n.setBody(truncate(body, 2000));
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        n.setSenderId(senderId);
        Notification saved = notificationRepository.save(n);
        eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId()));
        return saved;
    }

    /** A lead or opportunity was assigned to someone else: tell them, once the assignment commits. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRecordAssigned(RecordAssignedEvent e) {
        notify(e.tenantId(), e.assigneeId(), Notification.ASSIGNMENT, e.title(),
                e.detail() + (e.assignedByName() != null ? " · assigned by " + e.assignedByName() : ""),
                e.entityType(), e.entityId(), e.assignedById());
    }

    // ==================== Inbox ====================

    @Transactional(readOnly = true)
    public Inbox inbox(User user) {
        List<Notification> items = notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId(), PageRequest.of(0, INBOX_SIZE));
        Set<Long> senderIds = items.stream().map(Notification::getSenderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> senders = senderIds.isEmpty() ? Map.of() : userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return new Inbox(notificationRepository.countByUserIdAndReadAtIsNull(user.getId()),
                items.stream().map(n -> toRes(n, senders.get(n.getSenderId()))).toList());
    }

    @Transactional
    public void markRead(User user, Long id) {
        Notification n = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.markAllRead(user.getId(), LocalDateTime.now());
    }

    // ==================== Manager messages ====================

    /** To chosen people and/or a whole team — only people the sender manages (their visible users). */
    @Transactional
    public MessageRes sendMessage(User sender, MessageReq dto) {
        Long tenantId = salesForceScope.tenantId(sender);
        Map<Long, User> visible = salesForceScope.visibleUsers(sender).stream().collect(Collectors.toMap(User::getId, u -> u));
        Set<Long> recipients = new LinkedHashSet<>();
        if (dto.getUserIds() != null) {
            for (Long id : dto.getUserIds()) {
                if (!visible.containsKey(id)) {
                    throw ApiException.forbidden("You can only message people you manage");
                }
                recipients.add(id);
            }
        }
        if (dto.getTeamId() != null) {
            if (salesForceScope.visibleTeams(sender).stream().noneMatch(t -> t.getId().equals(dto.getTeamId()))) {
                throw ApiException.forbidden("You can only message teams you manage");
            }
            recipients.addAll(userRepository.findIdsByTenantIdAndTeamIdIn(tenantId, List.of(dto.getTeamId())));
        }
        recipients.remove(sender.getId());
        if (recipients.isEmpty()) {
            throw ApiException.badRequest("Choose at least one person or team to message");
        }
        for (Long id : recipients) {
            notify(tenantId, id, Notification.MESSAGE, dto.getTitle().trim(), dto.getBody().trim(), null, null, sender.getId());
        }
        return new MessageRes(recipients.size());
    }

    // ==================== Devices ====================

    /** Registers the app's push token for the signed-in user (moving it if the phone changed hands). */
    @Transactional
    public void registerDevice(User user, DeviceReq dto) {
        if (!PLATFORMS.contains(dto.getPlatform())) {
            throw ApiException.badRequest("Platform must be android or ios");
        }
        salesForceScope.tenantId(user);
        UserDevice device = deviceRepository.findByToken(dto.getToken()).orElseGet(UserDevice::new);
        device.setToken(dto.getToken());
        device.setUserId(user.getId());
        device.setPlatform(dto.getPlatform());
        device.setLastSeenAt(LocalDateTime.now());
        deviceRepository.save(device);
    }

    /** On sign-out: this phone stops receiving the user's pushes. */
    @Transactional
    public void unregisterDevice(User user, String token) {
        deviceRepository.findByToken(token).filter(d -> d.getUserId().equals(user.getId())).ifPresent(deviceRepository::delete);
    }

    private static NotificationRes toRes(Notification n, String senderName) {
        return new NotificationRes(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getEntityType(), n.getEntityId(),
                n.getSenderId(), senderName, n.getReadAt() != null, n.getCreatedAt());
    }

    private static String truncate(String v, int max) {
        return v == null ? null : v.length() > max ? v.substring(0, max - 1) + "…" : v;
    }
}
