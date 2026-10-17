package com.ucms.academic.repository;

import com.ucms.academic.domain.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {
    boolean existsByNameAndDeletedAtIsNull(String name);
    boolean existsByNameAndDeletedAtIsNullAndIdNot(String name, UUID id);
    List<AcademicYear> findByDeletedAtIsNullOrderByStartDateDesc();
    Optional<AcademicYear> findByIdAndDeletedAtIsNull(UUID id);
}
