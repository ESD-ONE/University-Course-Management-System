package com.ucms.academic.web.dto;

import com.ucms.academic.domain.CourseAcademicYear;
import java.time.LocalDate;
import java.util.UUID;

/** catalogCourseCode/Name, degreeProgramId, beginDate and yearOfStudy are the parent
 * course template's own fields, carried along so a year+program-scoped listing (see
 * DegreeProgramService.listCoursesForAssignment, the "Degree Programs" screen's
 * per-row course/lecturer detail) doesn't need a second round trip per course. */
public record CourseAcademicYearResponse(UUID id, UUID courseId, String catalogCourseCode, String catalogCourseName,
                                          UUID degreeProgramId, LocalDate beginDate, int yearOfStudy,
                                          UUID academicYearId, String academicYearName,
                                          UUID semesterId, String semesterName, int capacity, UUID lecturerId,
                                          String status) {
    public static CourseAcademicYearResponse from(CourseAcademicYear cay) {
        var course = cay.getCourse();
        return new CourseAcademicYearResponse(cay.getId(), course.getId(), course.getCatalogCourse().getCode(),
                course.getCatalogCourse().getName(), course.getDegreeProgram().getId(), course.getBeginDate(), course.getYearOfStudy(),
                cay.getAcademicYear().getId(), cay.getAcademicYear().getName(),
                cay.getSemester() == null ? null : cay.getSemester().getId(),
                cay.getSemester() == null ? null : cay.getSemester().getName(),
                cay.getCapacity() != null ? cay.getCapacity() : course.getCapacity(),
                cay.getLecturerId() != null ? cay.getLecturerId() : course.getLecturerId(),
                cay.getStatus().name());
    }
}
