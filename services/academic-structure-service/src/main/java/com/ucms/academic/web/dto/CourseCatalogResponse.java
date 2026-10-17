package com.ucms.academic.web.dto;

import com.ucms.academic.domain.CourseCatalog;
import java.util.UUID;

public record CourseCatalogResponse(UUID id, String code, String name, int defaultCredits, UUID owningDepartmentId) {
    public static CourseCatalogResponse from(CourseCatalog c) {
        return new CourseCatalogResponse(c.getId(), c.getCode(), c.getName(), c.getDefaultCredits(), c.getOwningDepartmentId());
    }
}
