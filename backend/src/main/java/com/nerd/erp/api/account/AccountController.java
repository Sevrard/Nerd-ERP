package com.nerd.erp.api.account;

import com.nerd.erp.api.account.AccountDtos.ChangePasswordRequest;
import com.nerd.erp.api.account.AccountDtos.ProfileResponse;
import com.nerd.erp.api.account.AccountDtos.ProfileUpdateRequest;
import com.nerd.erp.application.account.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Profil de l'utilisateur connecté (quel que soit son rôle). */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/profile")
    public ProfileResponse profile() {
        return accountService.profile();
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.updateProfile(request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(request);
    }
}
