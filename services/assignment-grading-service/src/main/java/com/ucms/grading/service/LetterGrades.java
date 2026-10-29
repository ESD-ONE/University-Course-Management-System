package com.ucms.grading.service;

import com.ucms.grading.client.BaseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a course percentage into a letter and GPA points using base-service's
 * "Standard Percentage" grading scale (bands stored as free text, e.g.
 * "A: 70-100 · B: 60-69 · C: 50-59 · D: 40-49 · F: 0-39"). No course is linked to a
 * specific scale in the data model yet, so the institution-wide standard scale applies to
 * every course. Cached for 5 minutes; if base-service is unreachable or the scale can't
 * be parsed, the same default bands are used rather than failing a grade view.
 */
@Component
public class LetterGrades {

    public record Band(String letter, double min, double max) {}

    public record Result(String letter, double points) {}

    private static final Logger log = LoggerFactory.getLogger(LetterGrades.class);
    private static final Pattern BAND = Pattern.compile("([A-Z][A-Za-z+\\-]*)\\s*:\\s*(\\d+(?:\\.\\d+)?)\\s*[–—-]\\s*(\\d+(?:\\.\\d+)?)");
    private static final List<Band> DEFAULT = List.of(
            new Band("A", 70, 100), new Band("B", 60, 69.99), new Band("C", 50, 59.99),
            new Band("D", 40, 49.99), new Band("F", 0, 39.99));
    private static final Map<String, Double> POINTS = Map.ofEntries(
            Map.entry("A+", 4.0), Map.entry("A", 4.0), Map.entry("A-", 3.7),
            Map.entry("B+", 3.3), Map.entry("B", 3.0), Map.entry("B-", 2.7),
            Map.entry("C+", 2.3), Map.entry("C", 2.0), Map.entry("C-", 1.7),
            Map.entry("D+", 1.3), Map.entry("D", 1.0), Map.entry("F", 0.0));

    private final BaseClient base;
    private volatile List<Band> bands;
    private volatile Instant loadedAt = Instant.EPOCH;

    public LetterGrades(BaseClient base) {
        this.base = base;
    }

    public Result of(double percentage) {
        for (Band b : bands()) {
            if (percentage >= b.min()) {
                return new Result(b.letter(), POINTS.getOrDefault(b.letter(), 0.0));
            }
        }
        return new Result("F", 0.0);
    }

    /** Bands sorted by lower bound, highest first. */
    List<Band> bands() {
        if (bands != null && Duration.between(loadedAt, Instant.now()).toMinutes() < 5) return bands;
        List<Band> loaded = DEFAULT;
        try {
            loaded = base.gradingScales().stream()
                    .filter(s -> "ACTIVE".equals(s.status()))
                    .sorted(Comparator.comparing((BaseClient.GradingScale s) -> !"Standard Percentage".equals(s.name())))
                    .map(s -> parse(s.bands()))
                    .filter(l -> l.size() >= 2 && l.stream().allMatch(b -> POINTS.containsKey(b.letter())))
                    .findFirst().orElse(DEFAULT);
        } catch (Exception ex) {
            log.warn("Using default grade bands, couldn't load grading scales: {}", ex.getMessage());
        }
        bands = loaded;
        loadedAt = Instant.now();
        return loaded;
    }

    static List<Band> parse(String text) {
        List<Band> result = new ArrayList<>();
        if (text == null) return result;
        Matcher m = BAND.matcher(text);
        while (m.find()) {
            result.add(new Band(m.group(1), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3))));
        }
        result.sort(Comparator.comparingDouble(Band::min).reversed());
        return result;
    }
}
