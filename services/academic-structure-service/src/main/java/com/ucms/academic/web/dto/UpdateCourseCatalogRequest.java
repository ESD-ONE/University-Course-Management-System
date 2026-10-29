package com.ucms.academic.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** code and owningDepartmentId aren't editable — code is the catalog subject's
 * permanent identity, and changing owningDepartmentId would silently move which
 * department authors it, mirroring UpdateDegreeProgramRequest's reasoning for
 * omitting code/departmentId there. */
public record UpdateCourseCatalogRequest(@NotBlank String name, @Positive int defaultCredits) {}
