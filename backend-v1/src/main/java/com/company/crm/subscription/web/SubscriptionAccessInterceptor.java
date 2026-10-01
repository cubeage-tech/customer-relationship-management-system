package com.company.crm.subscription.web;

import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Makes a lapsed tenant read-only: GETs keep working, any write is rejected with 402 until
 * the tenant renews. Billing and auth endpoints are excluded (see WebConfig) so a lapsed
 * tenant can still pay. Platform users (no tenant) are never restricted.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionAccessInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SubscriptionService subscriptionService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (READ_METHODS.contains(request.getMethod())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof User user
                && user.getTenant() != null) {
            subscriptionService.assertWriteAccess(user.getTenant());
        }
        return true;
    }
}
