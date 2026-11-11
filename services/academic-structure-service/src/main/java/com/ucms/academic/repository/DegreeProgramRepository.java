package com.ucms.academic.repository;

import com.ucms.academic.domain.DegreeProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DegreeProgramRepository extends JpaRepository<DegreeProgram, UUID>, JpaSpecificationExecutor<DegreeProgram> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    Optional<DegreeProgram> findByIdAndDeletedAtIsNull(UUID id);
}
