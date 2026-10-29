package com.ucms.base.repository;

import com.ucms.base.domain.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacultyRepository extends JpaRepository<Faculty, UUID> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
    List<Faculty> findByDeletedAtIsNullOrderByNameAsc();
    Optional<Faculty> findByIdAndDeletedAtIsNull(UUID id);
}
