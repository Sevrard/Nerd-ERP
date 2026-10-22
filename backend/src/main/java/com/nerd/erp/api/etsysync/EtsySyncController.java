package com.nerd.erp.api.etsysync;

import com.nerd.erp.application.etsysync.EtsySyncService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/etsy-sync")
public class EtsySyncController {
    private final EtsySyncService etsySyncService;

    public EtsySyncController(EtsySyncService etsySyncService) {
        this.etsySyncService = etsySyncService;
    }

    @PostMapping("/fetch")
    public EtsySyncFetchResponse fetch(@RequestParam UUID workspaceId) {
        return etsySyncService.fetch(workspaceId);
    }

    @PostMapping("/commit")
    public EtsySyncCommitResponse commit(@RequestParam UUID workspaceId, @Valid @RequestBody EtsySyncCommitRequest request) {
        return etsySyncService.commit(workspaceId, request);
    }

    @GetMapping("/sales-by-listing")
    public List<EtsyListingSalesStat> salesByListing(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return etsySyncService.salesByListing(workspaceId, from, to);
    }
}
