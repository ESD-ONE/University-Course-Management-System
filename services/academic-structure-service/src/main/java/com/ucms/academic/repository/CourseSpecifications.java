package com.ucms.academic.repository;

import com.ucms.academic.domain.Course;
import com.ucms.academic.domain.CourseStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Same pattern as identity-access-service's UserSpecifications: combine the Courses
 * screens' optional filters (degree program, year of study, status, search box) into
 * one query instead of fetching everything and filtering in memory. */
public final class CourseSpecifications {

    private CourseSpecifications() {}

    public static Specification<Course> filter(UUID degreeProgramId, Integer yearOfStudy, CourseStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (degreeProgramId != null) {
                predicates.add(cb.equal(root.get("degreeProgram").get("id"), degreeProgramId));
            }
            if (yearOfStudy != null) {
                predicates.add(cb.equal(root.get("yearOfStudy"), yearOfStudy));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                Join<Object, Object> catalog = root.join("catalogCourse");
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(catalog.get("name")), like),
                        cb.like(cb.lower(catalog.get("code")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** A lecturer's own courses, OR any course in a department they're scoped to (e.g. a
     * Department Head) — an empty scoped-department set means "no extra department
     * access", not "unfiltered". */
    public static Specification<Course> ownedByOrDepartmentScoped(UUID lecturerId, Collection<UUID> scopedDepartmentIds) {
        return (root, query, cb) -> {
            Predicate own = cb.equal(root.get("lecturerId"), lecturerId);
            if (scopedDepartmentIds.isEmpty()) return own;
            return cb.or(own, root.get("degreeProgram").get("departmentId").in(scopedDepartmentIds));
        };
    }
}
