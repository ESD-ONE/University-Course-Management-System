package com.ucms.base.repository;

import com.ucms.base.domain.Department;
import com.ucms.base.domain.ReferenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
    boolean existsByFacultyIdAndDeletedAtIsNull(UUID facultyId);
    Optional<Department> findByIdAndDeletedAtIsNull(UUID id);
    long countByDeletedAtIsNull();
    long countByDeletedAtIsNullAndStatus(ReferenceStatus status);
}
