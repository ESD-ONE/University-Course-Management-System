package com.ucms.academic.web.dto;

import com.ucms.academic.domain.ProgramCourse;
import java.util.UUID;

public record ProgramCourseResponse(UUID id, UUID catalogCourseId, String catalogCourseCode, String catalogCourseName,
                                     String requirementType, int recommendedYear, int recommendedSemester, int creditValue) {
    public static ProgramCourseResponse from(ProgramCourse pc) {
        return new ProgramCourseResponse(pc.getId(), pc.getCatalogCourse().getId(), pc.getCatalogCourse().getCode(),
                pc.getCatalogCourse().getName(), pc.getRequirementType().name(), pc.getRecommendedYear(),
                pc.getRecommendedSemester(), pc.getCreditValue());
    }
}
