package ch.nullprofile.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Service for recording usage metrics (MAU and authentication counts) per Relying Party
 * 
 * This service provides atomic, concurrency-safe operations for updating usage statistics
 * when users successfully authenticate with OIDC relying parties.
 */
@Service
public class UsageMeteringService {

    private static final Logger logger = LoggerFactory.getLogger(UsageMeteringService.class);
    private static final ZoneId EUROPE_ZURICH = ZoneId.of("Europe/Zurich");

    private final JdbcTemplate jdbcTemplate;

    public UsageMeteringService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Record a successful authentication event
     * 
     * This method:
     * 1. Upserts monthly_active_users to track unique users per RP per month
     * 2. Increments rp_monthly_counters to track total authentication count per RP per month
     * 
     * Both operations are atomic and safe under concurrent access.
     * 
     * @param relyingPartyId The UUID of the relying party (RP/client)
     * @param userId The UUID of the authenticated user
     * @param eventTime The timestamp of the authentication event
     */
    @Transactional
    public void recordSuccessfulAuthentication(UUID relyingPartyId, UUID userId, Instant eventTime) {
        // Convert eventTime to first day of month in Europe/Zurich timezone
        ZonedDateTime zonedDateTime = eventTime.atZone(EUROPE_ZURICH);
        LocalDate month = LocalDate.of(zonedDateTime.getYear(), zonedDateTime.getMonth(), 1);

        logger.debug("Recording authentication: rpId={}, userId={}, month={}", 
                relyingPartyId, userId, month);

        try {
            // 1. Upsert monthly_active_users
            // Insert new record or update last_seen_at if already exists
            String mauSql = """
                INSERT INTO monthly_active_users (relying_party_id, user_id, month, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (relying_party_id, user_id, month)
                DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
                """;
            
            int mauRows = jdbcTemplate.update(mauSql, 
                    relyingPartyId, 
                    userId, 
                    java.sql.Date.valueOf(month), 
                    java.sql.Timestamp.from(eventTime),
                    java.sql.Timestamp.from(eventTime));
            
            logger.debug("Updated monthly_active_users: rows={}", mauRows);

            // 2. Increment rp_monthly_counters
            // Insert with count=1 or increment existing count
            String counterSql = """
                INSERT INTO rp_monthly_counters (relying_party_id, month, auth_count, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?)
                ON CONFLICT (relying_party_id, month)
                DO UPDATE SET 
                    auth_count = rp_monthly_counters.auth_count + 1,
                    updated_at = EXCLUDED.updated_at
                """;
            
            Instant now = Instant.now();
            int counterRows = jdbcTemplate.update(counterSql, 
                    relyingPartyId, 
                    java.sql.Date.valueOf(month),
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now));
            
            logger.debug("Updated rp_monthly_counters: rows={}", counterRows);

            logger.info("Successfully recorded authentication: rpId={}, userId={}, month={}", 
                    relyingPartyId, userId, month);

        } catch (Exception e) {
            logger.error("Failed to record authentication metrics: rpId={}, userId={}, month={}", 
                    relyingPartyId, userId, month, e);
            // Re-throw to trigger transaction rollback
            throw new RuntimeException("Failed to record usage metrics", e);
        }
    }

    /**
     * Helper method to get the first day of the month for a given timestamp in Europe/Zurich timezone
     * Exposed for testing purposes
     */
    public LocalDate getMonthBucket(Instant eventTime) {
        ZonedDateTime zonedDateTime = eventTime.atZone(EUROPE_ZURICH);
        return LocalDate.of(zonedDateTime.getYear(), zonedDateTime.getMonth(), 1);
    }

    /**
     * Get monthly active user counts for a relying party over the specified number of months
     * 
     * @param relyingPartyId The UUID of the relying party (null for all RPs)
     * @param months Number of months to retrieve (default 6)
     * @return Map of month->MAU count
     */
    public java.util.Map<LocalDate, Integer> getMonthlyActiveUsers(UUID relyingPartyId, int months) {
        LocalDate endMonth = getMonthBucket(Instant.now());
        LocalDate startMonth = endMonth.minusMonths(months - 1);

        String sql;
        Object[] params;

        if (relyingPartyId != null) {
            sql = """
                SELECT month, COUNT(DISTINCT user_id) as mau_count
                FROM monthly_active_users
                WHERE relying_party_id = ? AND month >= ? AND month <= ?
                GROUP BY month
                ORDER BY month
                """;
            params = new Object[]{relyingPartyId, java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        } else {
            sql = """
                SELECT month, COUNT(DISTINCT user_id) as mau_count
                FROM monthly_active_users
                WHERE month >= ? AND month <= ?
                GROUP BY month
                ORDER BY month
                """;
            params = new Object[]{java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        }

        return jdbcTemplate.query(sql, params, rs -> {
            java.util.Map<LocalDate, Integer> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                LocalDate month = rs.getDate("month").toLocalDate();
                int count = rs.getInt("mau_count");
                result.put(month, count);
            }
            return result;
        });
    }

    /**
     * Get authentication counts for a relying party over the specified number of months
     * 
     * @param relyingPartyId The UUID of the relying party (null for all RPs)
     * @param months Number of months to retrieve (default 6)
     * @return Map of month->auth count
     */
    public java.util.Map<LocalDate, Integer> getAuthenticationCounts(UUID relyingPartyId, int months) {
        LocalDate endMonth = getMonthBucket(Instant.now());
        LocalDate startMonth = endMonth.minusMonths(months - 1);

        String sql;
        Object[] params;

        if (relyingPartyId != null) {
            sql = """
                SELECT month, auth_count
                FROM rp_monthly_counters
                WHERE relying_party_id = ? AND month >= ? AND month <= ?
                ORDER BY month
                """;
            params = new Object[]{relyingPartyId, java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        } else {
            sql = """
                SELECT month, SUM(auth_count) as auth_count
                FROM rp_monthly_counters
                WHERE month >= ? AND month <= ?
                GROUP BY month
                ORDER BY month
                """;
            params = new Object[]{java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        }

        return jdbcTemplate.query(sql, params, rs -> {
            java.util.Map<LocalDate, Integer> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                LocalDate month = rs.getDate("month").toLocalDate();
                int count = rs.getInt("auth_count");
                result.put(month, count);
            }
            return result;
        });
    }

    /**
     * Get retention rate (users from previous month who returned this month)
     * 
     * @param relyingPartyId The UUID of the relying party (null for all RPs)
     * @param months Number of months to retrieve (default 6)
     * @return Map of month->retention percentage (0-100)
     */
    public java.util.Map<LocalDate, Integer> getRetentionRate(UUID relyingPartyId, int months) {
        LocalDate endMonth = getMonthBucket(Instant.now());
        LocalDate startMonth = endMonth.minusMonths(months - 1);

        String sql;
        Object[] params;

        if (relyingPartyId != null) {
            sql = """
                WITH monthly_users AS (
                    SELECT month, user_id
                    FROM monthly_active_users
                    WHERE relying_party_id = ? AND month >= ? AND month <= ?
                ),
                retention_calc AS (
                    SELECT 
                        curr.month,
                        COUNT(DISTINCT curr.user_id) as current_users,
                        COUNT(DISTINCT prev.user_id) as retained_users
                    FROM monthly_users curr
                    LEFT JOIN monthly_users prev 
                        ON curr.user_id = prev.user_id 
                        AND prev.month = (curr.month - INTERVAL '1 month')::date
                    GROUP BY curr.month
                )
                SELECT 
                    month,
                    CASE 
                        WHEN current_users > 0 THEN ROUND((retained_users::numeric / current_users) * 100)
                        ELSE 0
                    END as retention_rate
                FROM retention_calc
                ORDER BY month
                """;
            params = new Object[]{relyingPartyId, java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        } else {
            sql = """
                WITH monthly_users AS (
                    SELECT month, user_id
                    FROM monthly_active_users
                    WHERE month >= ? AND month <= ?
                ),
                retention_calc AS (
                    SELECT 
                        curr.month,
                        COUNT(DISTINCT curr.user_id) as current_users,
                        COUNT(DISTINCT prev.user_id) as retained_users
                    FROM monthly_users curr
                    LEFT JOIN monthly_users prev 
                        ON curr.user_id = prev.user_id 
                        AND prev.month = (curr.month - INTERVAL '1 month')::date
                    GROUP BY curr.month
                )
                SELECT 
                    month,
                    CASE 
                        WHEN current_users > 0 THEN ROUND((retained_users::numeric / current_users) * 100)
                        ELSE 0
                    END as retention_rate
                FROM retention_calc
                ORDER BY month
                """;
            params = new Object[]{java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        }

        return jdbcTemplate.query(sql, params, rs -> {
            java.util.Map<LocalDate, Integer> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                LocalDate month = rs.getDate("month").toLocalDate();
                int rate = rs.getInt("retention_rate");
                result.put(month, rate);
            }
            return result;
        });
    }

    /**
     * Get count of users active in the last 30 days per month
     * 
     * @param relyingPartyId The UUID of the relying party (null for all RPs)
     * @param months Number of months to retrieve (default 6)
     * @return Map of month->active users count
     */
    public java.util.Map<LocalDate, Integer> getActiveRecently(UUID relyingPartyId, int months) {
        LocalDate endMonth = getMonthBucket(Instant.now());
        LocalDate startMonth = endMonth.minusMonths(months - 1);

        String sql;
        Object[] params;

        if (relyingPartyId != null) {
            sql = """
                SELECT 
                    month,
                    COUNT(*) as active_count
                FROM monthly_active_users
                WHERE relying_party_id = ?
                    AND month >= ? AND month <= ?
                    AND last_seen_at >= (month + INTERVAL '1 month' - INTERVAL '30 days')
                GROUP BY month
                ORDER BY month
                """;
            params = new Object[]{relyingPartyId, java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        } else {
            sql = """
                SELECT 
                    month,
                    COUNT(*) as active_count
                FROM monthly_active_users
                WHERE month >= ? AND month <= ?
                    AND last_seen_at >= (month + INTERVAL '1 month' - INTERVAL '30 days')
                GROUP BY month
                ORDER BY month
                """;
            params = new Object[]{java.sql.Date.valueOf(startMonth), java.sql.Date.valueOf(endMonth)};
        }

        return jdbcTemplate.query(sql, params, rs -> {
            java.util.Map<LocalDate, Integer> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                LocalDate month = rs.getDate("month").toLocalDate();
                int count = rs.getInt("active_count");
                result.put(month, count);
            }
            return result;
        });
    }
}
