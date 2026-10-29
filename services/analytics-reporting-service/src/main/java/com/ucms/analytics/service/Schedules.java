package com.ucms.analytics.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Frequency spec ⇄ next run time ⇄ human label ("Weekly · Mondays 08:00"). */
final class Schedules {
    private Schedules() {}

    static String spec(String frequency, String time, String dayOfWeek, Integer dayOfMonth) {
        return switch (frequency) {
            case "DAILY" -> "DAILY|" + time;
            case "WEEKLY" -> {
                if (dayOfWeek == null) throw new IllegalArgumentException("A weekly schedule needs a day of the week.");
                yield "WEEKLY|" + dayOfWeek + "|" + time;
            }
            case "MONTHLY" -> {
                if (dayOfMonth == null) throw new IllegalArgumentException("A monthly schedule needs a day of the month.");
                yield "MONTHLY|" + dayOfMonth + "|" + time;
            }
            default -> throw new IllegalArgumentException("Unknown frequency " + frequency);
        };
    }

    static String label(String spec) {
        String[] p = spec.split("\\|");
        return switch (p[0]) {
            case "DAILY" -> "Daily · " + p[1];
            case "WEEKLY" -> "Weekly · " + DayOfWeek.valueOf(p[1]).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s " + p[2];
            case "MONTHLY" -> "Monthly · day " + p[1] + " " + p[2];
            default -> spec;
        };
    }

    /** The first run strictly after {@code after}, in the given zone. */
    static OffsetDateTime next(String spec, OffsetDateTime after, ZoneId zone) {
        String[] p = spec.split("\\|");
        ZonedDateTime from = after.atZoneSameInstant(zone);
        LocalTime time = LocalTime.parse(p[p.length - 1]);
        ZonedDateTime candidate;
        switch (p[0]) {
            case "DAILY" -> {
                candidate = from.with(time);
                if (!candidate.isAfter(from)) candidate = candidate.plusDays(1);
            }
            case "WEEKLY" -> {
                DayOfWeek day = DayOfWeek.valueOf(p[1]);
                candidate = from.with(TemporalAdjusters.nextOrSame(day)).with(time);
                if (!candidate.isAfter(from)) candidate = candidate.plusWeeks(1);
            }
            case "MONTHLY" -> {
                int dom = Integer.parseInt(p[1]);
                candidate = from.withDayOfMonth(dom).with(time);
                if (!candidate.isAfter(from)) candidate = candidate.plusMonths(1).withDayOfMonth(dom);
            }
            default -> throw new IllegalArgumentException("Unknown schedule " + spec);
        }
        return candidate.toOffsetDateTime();
    }
}
