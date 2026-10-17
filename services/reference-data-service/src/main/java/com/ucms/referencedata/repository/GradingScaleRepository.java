package com.ucms.referencedata.repository;

import com.ucms.referencedata.domain.GradingScale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {
    List<GradingScale> findByDeletedAtIsNullOrderByNameAsc();
    Optional<GradingScale> findByIdAndDeletedAtIsNull(UUID id);
}
