package com.ucms.academic.repository;

import com.ucms.academic.domain.CourseCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseCatalogRepository extends JpaRepository<CourseCatalog, UUID> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    List<CourseCatalog> findByDeletedAtIsNullOrderByCodeAsc();
    List<CourseCatalog> findByOwningDepartmentIdAndDeletedAtIsNullOrderByCodeAsc(UUID owningDepartmentId);
    Optional<CourseCatalog> findByIdAndDeletedAtIsNull(UUID id);
}
