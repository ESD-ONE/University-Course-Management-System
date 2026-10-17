package com.ucms.referencedata.web.dto;

import com.ucms.referencedata.domain.Faculty;
import java.util.UUID;

public record FacultyResponse(UUID id, String name, String code, String status) {
    public static FacultyResponse from(Faculty f) {
        return new FacultyResponse(f.getId(), f.getName(), f.getCode(), f.getStatus().name());
    }
}
