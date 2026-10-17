package com.ucms.academic.repository;

import com.ucms.academic.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {
    boolean existsByAcademicYearIdAndNameAndDeletedAtIsNull(UUID academicYearId, String name);
    boolean existsByAcademicYearIdAndNameAndDeletedAtIsNullAndIdNot(UUID academicYearId, String name, UUID id);
    List<Semester> findByAcademicYearIdAndDeletedAtIsNullOrderBySequenceAsc(UUID academicYearId);
    Optional<Semester> findByIdAndAcademicYearIdAndDeletedAtIsNull(UUID id, UUID academicYearId);
}
