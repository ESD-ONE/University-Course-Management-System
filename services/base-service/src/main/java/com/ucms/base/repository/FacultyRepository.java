package com.ucms.base.repository;

import com.ucms.base.domain.Faculty;
import com.ucms.base.domain.ReferenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface FacultyRepository extends JpaRepository<Faculty, UUID>, JpaSpecificationExecutor<Faculty> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
    Optional<Faculty> findByIdAndDeletedAtIsNull(UUID id);
    long countByDeletedAtIsNull();
    long countByDeletedAtIsNullAndStatus(ReferenceStatus status);
}
