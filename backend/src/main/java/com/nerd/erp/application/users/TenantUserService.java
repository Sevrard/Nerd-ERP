package com.nerd.erp.application.users;

import com.nerd.erp.api.error.ApiException;
import com.nerd.erp.api.users.TenantUserDtos.InvitationResult;
import com.nerd.erp.api.users.TenantUserDtos.InviteUserRequest;
import com.nerd.erp.api.users.TenantUserDtos.TenantUserResponse;
import com.nerd.erp.api.users.TenantUserDtos.TenantUserUpdateRequest;
import com.nerd.erp.application.account.InvitationService;
import com.nerd.erp.application.account.InvitationService.Outcome;
import com.nerd.erp.domain.tenancy.AppUser;
import com.nerd.erp.domain.tenancy.Tenant;
import com.nerd.erp.domain.tenancy.TenantMembership;
import com.nerd.erp.domain.tenancy.TenantRole;
import com.nerd.erp.infrastructure.persistence.tenancy.AppUserRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantMembershipRepository;
import com.nerd.erp.infrastructure.persistence.tenancy.TenantRepository;
import com.nerd.erp.infrastructure.security.core.AuthenticatedUser;
import com.nerd.erp.infrastructure.security.core.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utilisateurs du tenant courant, gérés par ses OWNER / ADMIN. Règles :
 * <ul>
 *   <li>on ne modifie pas son propre accès (rôle, désactivation, retrait) ;</li>
 *   <li>seul un OWNER touche aux OWNER (attribuer ou retirer le rôle, modifier un propriétaire) ;</li>
 *   <li>il reste toujours au moins un OWNER actif ;</li>
 *   <li>la désactivation d'un compte est globale : refusée si l'utilisateur a accès à d'autres tenants.</li>
 * </ul>
 */
@Service
public class TenantUserService {

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final InvitationService invitationService;

    public TenantUserService(AppUserRepository appUserRepository,
                             TenantRepository tenantRepository,
                             TenantMembershipRepository membershipRepository,
                             InvitationService invitationService) {
        this.appUserRepository = appUserRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.invitationService = invitationService;
    }

    @Transactional(readOnly = true)
    public List<TenantUserResponse> list() {
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        List<TenantMembership> memberships = membershipRepository.findByTenantId(current.tenantId());
        Map<UUID, AppUser> users = appUserRepository.findAllById(
                        memberships.stream().map(TenantMembership::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return memberships.stream()
                .filter(m -> users.containsKey(m.getUserId()))
                .map(m -> toResponse(users.get(m.getUserId()), m, current.userId()))
                .sorted(Comparator.comparing((TenantUserResponse u) -> u.role().ordinal())
                        .thenComparing(TenantUserResponse::email))
                .toList();
    }

    @Transactional
    public InvitationResult invite(InviteUserRequest request) {
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        requireCanAssign(current, request.role());
        Tenant tenant = requireTenant(current.tenantId());
        Outcome outcome = invitationService.invite(tenant, request.email(), request.displayName(), request.role(),
                null, current.userId(), inviterName(current));
        return new InvitationResult(toResponse(outcome.user(), outcome.membership(), current.userId()),
                outcome.emailSent(), outcome.activationUrl());
    }

    @Transactional
    public InvitationResult resendInvitation(UUID userId) {
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        Tenant tenant = requireTenant(current.tenantId());
        TenantMembership membership = requireMembership(current.tenantId(), userId);
        AppUser user = requireUser(userId);
        requireCanManage(current, membership.getRole());
        Outcome outcome = invitationService.resend(tenant, user, current.userId(), inviterName(current));
        return new InvitationResult(toResponse(user, membership, current.userId()), outcome.emailSent(),
                outcome.activationUrl());
    }

    @Transactional
    public TenantUserResponse update(UUID userId, TenantUserUpdateRequest request) {
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        if (userId.equals(current.userId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_EDIT_SELF",
                    "Vous ne pouvez pas modifier votre propre accès");
        }
        TenantMembership membership = requireMembership(current.tenantId(), userId);
        AppUser user = requireUser(userId);
        requireCanManage(current, membership.getRole());

        if (request.role() != null && request.role() != membership.getRole()) {
            requireCanAssign(current, request.role());
            if (membership.getRole() == TenantRole.OWNER) {
                requireAnotherActiveOwner(current.tenantId(), userId);
            }
            membership.setRole(request.role());
            membershipRepository.save(membership);
        }

        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            if (!request.enabled()) {
                boolean sharedWithOtherTenants = membershipRepository.findByUserId(userId).stream()
                        .anyMatch(m -> !m.getTenantId().equals(current.tenantId()));
                if (sharedWithOtherTenants) {
                    throw new ApiException(HttpStatus.CONFLICT, "USER_SHARED",
                            "Cet utilisateur a aussi accès à d'autres comptes : retirez-lui l'accès plutôt que de le désactiver");
                }
                if (membership.getRole() == TenantRole.OWNER) {
                    requireAnotherActiveOwner(current.tenantId(), userId);
                }
            }
            user.setEnabled(request.enabled());
            appUserRepository.save(user);
        }
        return toResponse(user, membership, current.userId());
    }

    /** Retire l'accès au tenant courant (le compte global subsiste s'il est utilisé ailleurs). */
    @Transactional
    public void remove(UUID userId) {
        AuthenticatedUser current = SecurityContextHelper.requireUser();
        if (userId.equals(current.userId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_EDIT_SELF",
                    "Vous ne pouvez pas retirer votre propre accès");
        }
        TenantMembership membership = requireMembership(current.tenantId(), userId);
        requireCanManage(current, membership.getRole());
        if (membership.getRole() == TenantRole.OWNER) {
            requireAnotherActiveOwner(current.tenantId(), userId);
        }
        membershipRepository.delete(membership);
    }

    // ---------------------------------------------------------------- règles

    private static void requireCanAssign(AuthenticatedUser current, TenantRole role) {
        if (role == TenantRole.OWNER && current.role() != TenantRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED",
                    "Seul un administrateur principal peut attribuer ce rôle");
        }
    }

    private static void requireCanManage(AuthenticatedUser current, TenantRole targetRole) {
        if (targetRole == TenantRole.OWNER && current.role() != TenantRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED",
                    "Seul un administrateur principal peut modifier un autre administrateur principal");
        }
    }

    private void requireAnotherActiveOwner(UUID tenantId, UUID excludedUserId) {
        List<UUID> otherOwnerIds = membershipRepository.findByTenantId(tenantId).stream()
                .filter(m -> m.getRole() == TenantRole.OWNER && !m.getUserId().equals(excludedUserId))
                .map(TenantMembership::getUserId)
                .toList();
        boolean ok = !otherOwnerIds.isEmpty() && appUserRepository.findAllById(otherOwnerIds).stream()
                .anyMatch(u -> u.isEnabled() && u.isPasswordSet());
        if (!ok) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_OWNER",
                    "Le compte doit conserver au moins un administrateur principal actif");
        }
    }

    // ---------------------------------------------------------------- helpers

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Compte introuvable"));
    }

    private TenantMembership requireMembership(UUID tenantId, UUID userId) {
        return membershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Utilisateur introuvable dans ce compte"));
    }

    private AppUser requireUser(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    private String inviterName(AuthenticatedUser current) {
        return appUserRepository.findById(current.userId()).map(AppUser::getDisplayName).orElse(current.email());
    }

    static TenantUserResponse toResponse(AppUser user, TenantMembership membership, UUID currentUserId) {
        return new TenantUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), membership.getRole(),
                user.isEnabled(), !user.isPasswordSet(), user.getLastLoginAt(), membership.getCreatedAt(),
                user.getId().equals(currentUserId));
    }
}
