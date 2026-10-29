package com.ucms.base.repository;

import com.ucms.base.domain.CourseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {
    List<CourseCategory> findByDeletedAtIsNullOrderByNameAsc();
    Optional<CourseCategory> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByNameAndDeletedAtIsNull(String name);
    boolean existsByNameAndDeletedAtIsNullAndIdNot(String name, UUID id);
}
