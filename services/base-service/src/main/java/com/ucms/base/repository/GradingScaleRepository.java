package com.ucms.base.repository;

import com.ucms.base.domain.GradingScale;
import com.ucms.base.domain.ReferenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID>, JpaSpecificationExecutor<GradingScale> {
    Optional<GradingScale> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByNameAndDeletedAtIsNull(String name);
    boolean existsByNameAndDeletedAtIsNullAndIdNot(String name, UUID id);
    long countByDeletedAtIsNull();
    long countByDeletedAtIsNullAndStatus(ReferenceStatus status);
}
