package com.ucms.referencedata.web.dto;

import com.ucms.referencedata.domain.CourseCategory;
import java.util.UUID;

public record CourseCategoryResponse(UUID id, String name, String description, String status) {
    public static CourseCategoryResponse from(CourseCategory c) {
        return new CourseCategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getStatus().name());
    }
}
