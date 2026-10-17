package com.ucms.academic.web.dto;

import com.ucms.academic.domain.DegreeProgram;
import java.util.UUID;

public record DegreeProgramResponse(UUID id, String code, String name, UUID departmentId,
                                     int durationYears, int totalCredits, String status) {
    public static DegreeProgramResponse from(DegreeProgram p) {
        return new DegreeProgramResponse(p.getId(), p.getCode(), p.getName(), p.getDepartmentId(),
                p.getDurationYears(), p.getTotalCredits(), p.getStatus().name());
    }
}
