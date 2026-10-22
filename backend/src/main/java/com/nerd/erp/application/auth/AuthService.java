package com.nerd.erp.application.auth;

import com.nerd.erp.api.auth.LoginRequest;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.infrastructure.security.core.AuthenticatedUser;
import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.tenancy.TenantMembership;
import com.nerd.erp.infrastructure.persistence.tenancy.AppUserRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import com.nerd.erp.infrastructure.security.jwt.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository,
                       TenantRepository tenantRepository,
                       TenantMembershipRepository membershipRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        Tenant tenant = tenantRepository.findBySlug(request.tenantSlug())
                .filter(Tenant::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid tenant"));
        TenantMembership membership = membershipRepository.findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "No access to tenant"));
        AuthenticatedUser authUser = new AuthenticatedUser(user.getId(), tenant.getId(), membership.getRole(),
                user.getEmail());
        String token = jwtService.generateToken(authUser);
        return new LoginResponse(token, user.getId(), tenant.getId(), membership.getRole(), user.getEmail());
    }
}
