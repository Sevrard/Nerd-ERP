package com.nerd.erp.infrastructure.security.core;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.application.module.ModuleAccessService;
import com.nerd.erp.application.tenancy.TenantStatusService;
import com.nerd.erp.domain.module.ModuleCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Pour toute requête authentifiée côté tenant :
 * <ul>
 *   <li>refuse l'accès si le tenant est suspendu ({@code TENANT_SUSPENDED}) ;</li>
 *   <li>refuse l'accès aux endpoints annotés {@link RequireModule} dont le module n'est pas activé
 *       pour le tenant ({@code MODULE_DISABLED}).</li>
 * </ul>
 * Les administrateurs plateforme ne sont pas soumis au contrôle de module (ils n'utilisent pas les
 * endpoints métier d'un autre tenant), mais restent soumis à la suspension de leur propre tenant.
 */
@Component
public class ModuleAccessInterceptor implements HandlerInterceptor {

    public static final String MODULE_DISABLED = "MODULE_DISABLED";
    public static final String TENANT_SUSPENDED = "TENANT_SUSPENDED";

    private final ModuleAccessService moduleAccessService;
    private final TenantStatusService tenantStatusService;

    public ModuleAccessInterceptor(ModuleAccessService moduleAccessService, TenantStatusService tenantStatusService) {
        this.moduleAccessService = moduleAccessService;
        this.tenantStatusService = tenantStatusService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return true;
        }
        if (!tenantStatusService.isActive(user.tenantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, TENANT_SUSPENDED, "Compte suspendu. Contactez votre éditeur.");
        }
        RequireModule required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequireModule.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireModule.class);
        }
        if (required == null) {
            return true;
        }
        ModuleCode code = required.value();
        if (!moduleAccessService.isEnabled(user.tenantId(), code)) {
            throw new ApiException(HttpStatus.FORBIDDEN, MODULE_DISABLED,
                    "Module « " + code.label() + " » non activé pour ce compte.");
        }
        return true;
    }
}
