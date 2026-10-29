package com.ucms.base.repository;

import com.ucms.base.domain.GradingScale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {
    List<GradingScale> findByDeletedAtIsNullOrderByNameAsc();
    Optional<GradingScale> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByNameAndDeletedAtIsNull(String name);
    boolean existsByNameAndDeletedAtIsNullAndIdNot(String name, UUID id);
}
