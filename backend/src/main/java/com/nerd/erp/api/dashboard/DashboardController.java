package com.nerd.erp.api.dashboard;

import com.nerd.erp.api.dashboard.DashboardSummaryResponse;
import com.nerd.erp.api.dashboard.RevenueTrendResponse;
import com.nerd.erp.application.dashboard.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean allPeriods) {
        return dashboardService.summary(workspaceId, from, to, allPeriods);
    }

    @GetMapping("/product-sales")
    public List<ProductSalesStat> productSales(
            @RequestParam UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean allPeriods) {
        return dashboardService.productSales(workspaceId, from, to, allPeriods);
    }

    @GetMapping("/upcoming-actions")
    public Object upcomingActions(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(defaultValue = "14") int days) {
        return dashboardService.upcomingActions(workspaceId, days);
    }

    @GetMapping("/revenue-trend")
    public RevenueTrendResponse revenueTrend(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean allPeriods) {
        return dashboardService.revenueTrend(workspaceId, months, from, to, allPeriods);
    }
}
