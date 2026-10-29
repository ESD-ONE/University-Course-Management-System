package com.ucms.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-caller request quotas, by tier. A request falls into the first tier whose methods
 * (any, if none listed) and path patterns match it; each tier has its own bucket per
 * caller. See docs/security-loadbalancing-ratelimit-plan.md §4.4.
 */
@ConfigurationProperties("ucms.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private List<Tier> tiers = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<Tier> getTiers() { return tiers; }
    public void setTiers(List<Tier> tiers) { this.tiers = tiers; }

    public static class Tier {
        private String name;
        private List<String> methods = new ArrayList<>();
        private List<String> paths = new ArrayList<>();
        /** Not limited here at all (e.g. the long-lived live-notification stream). */
        private boolean exempt;
        /** Sustained rate: {@code limit} requests per {@code period}... */
        private int limit = 20;
        private Duration period = Duration.ofSeconds(1);
        /** ...with up to {@code burst} at once after a quiet spell. */
        private int burst = 40;

        public double tokensPerSecond() {
            return limit / (period.toMillis() / 1000.0);
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
        public boolean isExempt() { return exempt; }
        public void setExempt(boolean exempt) { this.exempt = exempt; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public Duration getPeriod() { return period; }
        public void setPeriod(Duration period) { this.period = period; }
        public int getBurst() { return burst; }
        public void setBurst(int burst) { this.burst = burst; }
    }
}
