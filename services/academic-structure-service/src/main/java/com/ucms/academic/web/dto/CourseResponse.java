package com.ucms.academic.web.dto;

import com.ucms.academic.domain.Course;
import java.time.LocalDate;
import java.util.UUID;

public record CourseResponse(UUID id, UUID catalogCourseId, String catalogCourseCode, String catalogCourseName,
                              UUID degreeProgramId, String degreeProgramCode, String degreeProgramName,
                              LocalDate beginDate, int yearOfStudy,
                              UUID lecturerId, int capacity, String status) {
    public static CourseResponse from(Course c) {
        return new CourseResponse(c.getId(), c.getCatalogCourse().getId(), c.getCatalogCourse().getCode(),
                c.getCatalogCourse().getName(), c.getDegreeProgram().getId(), c.getDegreeProgram().getCode(),
                c.getDegreeProgram().getName(), c.getBeginDate(), c.getYearOfStudy(),
                c.getLecturerId(), c.getCapacity(), c.getStatus().name());
    }
}
