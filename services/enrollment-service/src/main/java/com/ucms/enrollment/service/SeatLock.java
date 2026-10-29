package com.ucms.enrollment.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

/**
 * Serializes seat-taking per course offering. The count-then-insert in
 * CourseEnrollmentService is a check-then-act; without this, two students racing for
 * the last seat could both see "1 left" and both be enrolled. A Postgres
 * transaction-scoped advisory lock keyed by the offering id makes concurrent
 * enrollments into the same offering queue up, while different offerings never block
 * each other. Released automatically at commit/rollback.
 *
 * There's no seat-counter row to SELECT ... FOR UPDATE on — capacity lives in
 * academic-structure-service — which is why this is an advisory lock rather than a row
 * lock. H2 (tests) has no advisory locks; there it's a no-op.
 */
@Component
public class SeatLock {

    @PersistenceContext
    private EntityManager entityManager;

    private final boolean postgres;

    public SeatLock(DataSource dataSource) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            this.postgres = c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        }
    }

    /** Must be called inside the transaction doing the enrollment write. */
    public void lock(UUID offeringId) {
        if (!postgres) return;
        long key = offeringId.getMostSignificantBits() ^ offeringId.getLeastSignificantBits();
        entityManager.createNativeQuery("select 1 from (select pg_advisory_xact_lock(:k)) as l")
                .setParameter("k", key)
                .getSingleResult();
    }
}
