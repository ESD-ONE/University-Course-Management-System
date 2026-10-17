package com.ucms.academic.web.dto;

import com.ucms.academic.domain.DegreeProgramAcademicYear;
import java.util.UUID;

public record DegreeProgramAcademicYearResponse(UUID id, UUID degreeProgramId, String degreeProgramCode,
                                                 String degreeProgramName, UUID academicYearId, String academicYearName,
                                                 int durationYears, int totalCredits, String status) {
    public static DegreeProgramAcademicYearResponse from(DegreeProgramAcademicYear a) {
        var program = a.getDegreeProgram();
        return new DegreeProgramAcademicYearResponse(a.getId(), program.getId(), program.getCode(), program.getName(),
                a.getAcademicYear().getId(), a.getAcademicYear().getName(),
                a.getDurationYears() != null ? a.getDurationYears() : program.getDurationYears(),
                a.getTotalCredits() != null ? a.getTotalCredits() : program.getTotalCredits(),
                a.getStatus().name());
    }
}
