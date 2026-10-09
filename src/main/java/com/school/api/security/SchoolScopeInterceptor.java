package com.school.api.security;

import com.school.api.common.domain.Role;
import com.school.api.common.exception.ForbiddenException;
import com.school.api.common.web.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces per-school data scoping for callers that are not platform admins: a
 * school-scoped token (one carrying {@code school_id}) may only reach
 * {@code /schools/{their-id}} and its nested resources. Collection endpoints such as
 * listing all schools stay platform-admin only. Denials surface as 403 problem details.
 */
@Component
public class SchoolScopeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }
        if (CurrentUser.isPlatformAdmin(authentication)) {
            return true;
        }
        String schoolId = resolveRequestedSchoolId(request);
        String tokenSchoolId = tokenSchoolId(authentication);
        if (schoolId != null && schoolId.equals(tokenSchoolId)) {
            return true;
        }
        throw new ForbiddenException("Caller is not scoped to the requested school");
    }

    /**
     * Returns the school the request is asking for: the {@code schoolId} variable on
     * nested routes (branches, owner), or the {@code id} variable under
     * {@code /schools}. {@code null} means a collection-level request, which only
     * platform admins may perform.
     */
    private String resolveRequestedSchoolId(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return null;
        }
        Object schoolId = variables.get("schoolId");
        if (schoolId != null) {
            return schoolId.toString();
        }
        Object id = variables.get("id");
        if (id != null && request.getRequestURI().startsWith(ApiPaths.V1 + "/schools/")) {
            return id.toString();
        }
        return null;
    }

    private String tokenSchoolId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getClaimAsString("school_id");
        }
        if (principal instanceof AuthenticatedUser user) {
            UUID schoolId = user.getAccount().getSchoolId();
            return schoolId == null ? null : schoolId.toString();
        }
        return null;
    }
}
