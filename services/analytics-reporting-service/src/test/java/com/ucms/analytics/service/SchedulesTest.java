package com.ucms.analytics.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulesTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    @Test
    void daily_laterToday_orTomorrowIfTheTimeHasPassed() {
        OffsetDateTime morning = OffsetDateTime.of(2026, 10, 26, 5, 0, 0, 0, ZoneOffset.UTC);
        assertThat(Schedules.next("DAILY|06:00", morning, UTC)).isEqualTo(morning.withHour(6));
        OffsetDateTime evening = morning.withHour(20);
        assertThat(Schedules.next("DAILY|06:00", evening, UTC)).isEqualTo(morning.plusDays(1).withHour(6));
    }

    @Test
    void weekly_nextMatchingWeekday() {
        // 2026-10-26 is a Monday.
        OffsetDateTime mondayAfterRun = OffsetDateTime.of(2026, 10, 26, 9, 0, 0, 0, ZoneOffset.UTC);
        assertThat(Schedules.next("WEEKLY|MONDAY|08:00", mondayAfterRun, UTC))
                .isEqualTo(OffsetDateTime.of(2026, 11, 2, 8, 0, 0, 0, ZoneOffset.UTC));
        assertThat(Schedules.next("WEEKLY|WEDNESDAY|08:00", mondayAfterRun, UTC))
                .isEqualTo(OffsetDateTime.of(2026, 10, 28, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void monthly_rollsToNextMonthOncePast() {
        OffsetDateTime after = OffsetDateTime.of(2026, 10, 26, 9, 0, 0, 0, ZoneOffset.UTC);
        assertThat(Schedules.next("MONTHLY|1|08:00", after, UTC))
                .isEqualTo(OffsetDateTime.of(2026, 11, 1, 8, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void scheduleTimesAreInterpretedInTheConfiguredZone() {
        OffsetDateTime after = OffsetDateTime.of(2026, 10, 26, 0, 0, 0, 0, ZoneOffset.UTC);
        // 08:00 in Colombo (UTC+05:30) is 02:30 UTC.
        assertThat(Schedules.next("DAILY|08:00", after, ZoneId.of("Asia/Colombo")).toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 10, 26, 2, 30, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    void labels() {
        assertThat(Schedules.label("WEEKLY|MONDAY|08:00")).isEqualTo("Weekly · Mondays 08:00");
        assertThat(Schedules.label("DAILY|06:00")).isEqualTo("Daily · 06:00");
        assertThat(Schedules.label("MONTHLY|1|08:00")).isEqualTo("Monthly · day 1 08:00");
    }
}
