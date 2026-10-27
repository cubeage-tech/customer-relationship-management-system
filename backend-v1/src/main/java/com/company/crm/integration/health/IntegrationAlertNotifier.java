package com.company.crm.integration.health;

import com.company.crm.common.enums.RoleType;
import com.company.crm.notification.service.StaffNotifier;
import com.company.crm.notification.settings.NotificationEvent;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * INT-8: tells the platform admins (in-app and by email) which integration to re-authorise and
 * how. Email is skipped by the mail server itself when it is the mail server that failed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationAlertNotifier {

    private final UserRepository userRepository;
    private final StaffNotifier staffNotifier;

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuthFailed(IntegrationAuthFailedEvent event) {
        List<User> admins = userRepository.findActiveByRole(RoleType.SUPER_ADMIN);
        if (admins.isEmpty()) {
            log.error("No platform admin to tell that {} needs re-authorising", event.integration());
            return;
        }
        IntegrationHealthService.Integration i = IntegrationHealthService.KNOWN.get(event.integration());
        String label = i != null ? i.label() : event.integration();
        try {
            staffNotifier.notify(null, NotificationEvent.INTEGRATION_AUTH_FAILED, admins,
                    "Re-authorise " + label,
                    label + " rejected the CRM's credentials" + (event.detail() != null ? " (" + event.detail() + ")" : "") + ". "
                            + (i != null ? i.fix() : "Update its credentials and restart the backend."),
                    "integration", null, null, "/platform/settings");
        } catch (RuntimeException e) {
            log.warn("Could not notify the platform admins about {}: {}", event.integration(), e.getMessage());
        }
    }
}
