package com.ucms.academic.repository;

import com.ucms.academic.domain.CourseCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CourseCatalogRepository extends JpaRepository<CourseCatalog, UUID>, JpaSpecificationExecutor<CourseCatalog> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    Optional<CourseCatalog> findByIdAndDeletedAtIsNull(UUID id);
}
