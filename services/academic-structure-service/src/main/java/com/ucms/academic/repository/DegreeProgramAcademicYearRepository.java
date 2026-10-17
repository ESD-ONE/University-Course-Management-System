package com.ucms.academic.repository;

import com.ucms.academic.domain.DegreeProgramAcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DegreeProgramAcademicYearRepository extends JpaRepository<DegreeProgramAcademicYear, UUID> {
    List<DegreeProgramAcademicYear> findByAcademicYearIdAndDeletedAtIsNull(UUID academicYearId);
    List<DegreeProgramAcademicYear> findByDegreeProgramIdAndDeletedAtIsNull(UUID degreeProgramId);
    List<DegreeProgramAcademicYear> findByDeletedAtIsNull();
    Optional<DegreeProgramAcademicYear> findByDegreeProgramIdAndAcademicYearIdAndDeletedAtIsNull(UUID degreeProgramId, UUID academicYearId);
    Optional<DegreeProgramAcademicYear> findByIdAndDegreeProgramIdAndDeletedAtIsNull(UUID id, UUID degreeProgramId);
    boolean existsByDegreeProgramIdAndAcademicYearIdAndDeletedAtIsNull(UUID degreeProgramId, UUID academicYearId);
}
