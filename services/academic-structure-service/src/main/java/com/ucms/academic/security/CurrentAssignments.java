package com.ucms.academic.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

/** Reads the current request's scope assignments out of Authentication.details, where
 *  JwtAuthFilter put them: either the list itself, or a {@link Lazy} that loads them
 *  (once per request) the first time a permission check asks. */
public final class CurrentAssignments {
    private CurrentAssignments() {}

    @SuppressWarnings("unchecked")
    public static List<AssignmentClaim> get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        if (auth.getDetails() instanceof Lazy lazy) return lazy.get();
        if (auth.getDetails() instanceof List<?> list) return (List<AssignmentClaim>) list;
        return List.of();
    }

    /** Loads on first use and remembers the result for the rest of the request. */
    public static final class Lazy {
        private final Supplier<List<AssignmentClaim>> loader;
        private List<AssignmentClaim> value;

        public Lazy(Supplier<List<AssignmentClaim>> loader) {
            this.loader = loader;
        }

        public synchronized List<AssignmentClaim> get() {
            if (value == null) value = loader.get();
            return value;
        }
    }
}
