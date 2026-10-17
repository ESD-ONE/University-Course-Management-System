package com.ucms.referencedata.repository;

import com.ucms.referencedata.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
    boolean existsByFacultyIdAndDeletedAtIsNull(UUID facultyId);
    List<Department> findByDeletedAtIsNullOrderByNameAsc();
    List<Department> findByFacultyIdAndDeletedAtIsNullOrderByNameAsc(UUID facultyId);
    Optional<Department> findByIdAndDeletedAtIsNull(UUID id);
}
