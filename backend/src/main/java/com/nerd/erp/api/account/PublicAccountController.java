package com.nerd.erp.api.account;

import com.nerd.erp.api.account.AccountDtos.ActivateRequest;
import com.nerd.erp.api.account.AccountDtos.ForgotPasswordRequest;
import com.nerd.erp.api.account.AccountDtos.ResetPasswordRequest;
import com.nerd.erp.api.account.AccountDtos.TokenInfoResponse;
import com.nerd.erp.api.auth.LoginResponse;
import com.nerd.erp.application.account.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Parcours sans session : mot de passe oublié, réinitialisation, activation d'une invitation. */
@RestController
@RequestMapping("/api/v1/auth/public")
public class PublicAccountController {

    private final AccountService accountService;

    public PublicAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountService.forgotPassword(request.email());
    }

    @GetMapping("/token-info")
    public TokenInfoResponse tokenInfo(@RequestParam String token) {
        return accountService.tokenInfo(token);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request);
    }

    @PostMapping("/activate")
    public LoginResponse activate(@Valid @RequestBody ActivateRequest request) {
        return accountService.activate(request);
    }
}
