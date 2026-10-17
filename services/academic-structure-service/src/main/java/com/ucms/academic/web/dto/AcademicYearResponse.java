package com.ucms.academic.web.dto;

import com.ucms.academic.domain.AcademicYear;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AcademicYearResponse(UUID id, String name, LocalDate startDate, LocalDate endDate,
                                    String status, List<SemesterResponse> semesters) {
    public static AcademicYearResponse from(AcademicYear y, List<SemesterResponse> semesters) {
        return new AcademicYearResponse(y.getId(), y.getName(), y.getStartDate(), y.getEndDate(),
                y.getStatus().name(), semesters);
    }
}
