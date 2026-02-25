package ch.nullprofile.dto;

import java.util.List;

/**
 * Response DTO for usage summary endpoint
 */
public record UsageSummaryResponse(
    List<String> months,
    List<Integer> mau,
    List<Integer> logins,
    List<Integer> retention,
    List<Integer> activeRecently
) {}
