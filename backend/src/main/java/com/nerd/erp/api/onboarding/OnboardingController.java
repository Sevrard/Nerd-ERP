package com.nerd.erp.api.onboarding;

import com.nerd.erp.application.onboarding.OnboardingService;
import com.nerd.erp.application.onboarding.OnboardingService.Checklist;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Guide de démarrage : checklist calculée sur l'état réel du workspace. */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/checklist")
    public Checklist checklist(@RequestParam UUID workspaceId) {
        return onboardingService.checklist(workspaceId);
    }
}
