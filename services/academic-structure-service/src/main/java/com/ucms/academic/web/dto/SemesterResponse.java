package com.ucms.academic.web.dto;

import com.ucms.academic.domain.Semester;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SemesterResponse(UUID id, String name, int sequence, OffsetDateTime enrollmentOpenAt,
                                OffsetDateTime enrollmentCloseAt, String status) {
    public static SemesterResponse from(Semester s) {
        return new SemesterResponse(s.getId(), s.getName(), s.getSequence(),
                s.getEnrollmentOpenAt(), s.getEnrollmentCloseAt(), s.getStatus().name());
    }
}
