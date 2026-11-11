package com.ucms.base.web.dto;

/** Total/active/inactive counts for one reference-data tab, independent of the current
 * page or search filter — same idea as identity-access-service's UserSummaryResponse. */
public record ReferenceCounts(long total, long active, long inactive) {
    public static ReferenceCounts of(long total, long active) {
        return new ReferenceCounts(total, active, total - active);
    }
}
