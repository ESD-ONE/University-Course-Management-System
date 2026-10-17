package com.ucms.identity.web.dto;

/**
 * Counts computed server-side (COUNT ... GROUP BY), not derived from whatever
 * page or filter happens to be loaded client-side — backs the Users screen's
 * summary tiles regardless of which role tab or filter is currently active.
 */
public record UserSummaryResponse(long total, long active, long students, long staff) {}
