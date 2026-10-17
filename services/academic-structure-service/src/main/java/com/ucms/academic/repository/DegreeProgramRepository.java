package com.ucms.academic.repository;

import com.ucms.academic.domain.DegreeProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DegreeProgramRepository extends JpaRepository<DegreeProgram, UUID> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    List<DegreeProgram> findByDeletedAtIsNullOrderByNameAsc();
    List<DegreeProgram> findByDepartmentIdAndDeletedAtIsNullOrderByNameAsc(UUID departmentId);
    Optional<DegreeProgram> findByIdAndDeletedAtIsNull(UUID id);
}
