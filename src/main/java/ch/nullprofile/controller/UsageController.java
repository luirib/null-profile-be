package ch.nullprofile.controller;

import ch.nullprofile.dto.UsageSummaryResponse;
import ch.nullprofile.service.UsageMeteringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for usage metrics endpoints
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UsageMeteringService usageMeteringService;

    public UsageController(UsageMeteringService usageMeteringService) {
        this.usageMeteringService = usageMeteringService;
    }

    /**
     * Get usage summary statistics for the specified relying party
     * 
     * @param rpIdParam Optional relying party ID ("ALL" for all RPs, or a UUID string)
     * @param months Number of months to retrieve (default 6)
     * @return Usage summary with monthly data points
     */
    @GetMapping("/summary")
    public ResponseEntity<UsageSummaryResponse> getUsageSummary(
            @RequestParam(required = false) String rpId,
            @RequestParam(defaultValue = "6") int months) {

        if (months < 1 || months > 24) {
            months = 6; // Default to 6 if invalid
        }

        // Parse rpId - handle "ALL" or UUID
        UUID relyingPartyId = null;
        if (rpId != null && !rpId.equalsIgnoreCase("ALL")) {
            try {
                relyingPartyId = UUID.fromString(rpId);
            } catch (IllegalArgumentException e) {
                // Invalid UUID format, treat as null (all RPs)
                relyingPartyId = null;
            }
        }

        // Get all metrics
        Map<LocalDate, Integer> mauData = usageMeteringService.getMonthlyActiveUsers(relyingPartyId, months);
        Map<LocalDate, Integer> authData = usageMeteringService.getAuthenticationCounts(relyingPartyId, months);
        Map<LocalDate, Integer> retentionData = usageMeteringService.getRetentionRate(relyingPartyId, months);
        Map<LocalDate, Integer> activeRecentlyData = usageMeteringService.getActiveRecently(relyingPartyId, months);

        // Collect all unique months across all datasets (keeping insertion order)
        Set<LocalDate> allMonthsSet = new LinkedHashSet<>();
        allMonthsSet.addAll(mauData.keySet());
        allMonthsSet.addAll(authData.keySet());
        allMonthsSet.addAll(retentionData.keySet());
        allMonthsSet.addAll(activeRecentlyData.keySet());
        List<LocalDate> allMonths = new ArrayList<>(allMonthsSet);

        // Convert to parallel arrays
        List<String> monthStrings = new ArrayList<>();
        List<Integer> mauValues = new ArrayList<>();
        List<Integer> loginValues = new ArrayList<>();
        List<Integer> retentionValues = new ArrayList<>();
        List<Integer> activeRecentlyValues = new ArrayList<>();

        for (LocalDate month : allMonths) {
            monthStrings.add(month.format(MONTH_FORMATTER));
            mauValues.add(mauData.getOrDefault(month, 0));
            loginValues.add(authData.getOrDefault(month, 0));
            retentionValues.add(retentionData.getOrDefault(month, 0));
            activeRecentlyValues.add(activeRecentlyData.getOrDefault(month, 0));
        }

        // Create response
        UsageSummaryResponse response = new UsageSummaryResponse(
                monthStrings,
                mauValues,
                loginValues,
                retentionValues,
                activeRecentlyValues
        );

        return ResponseEntity.ok(response);
    }
}
