package com.ucms.academic.web.dto;

import com.ucms.academic.domain.RequirementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateProgramCourseRequest(@NotNull RequirementType requirementType,
                                          @Positive int recommendedYear, @Positive int recommendedSemester,
                                          @Positive int creditValue) {}
