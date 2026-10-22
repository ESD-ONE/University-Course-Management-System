package com.ucms.academic.web;

import com.ucms.academic.service.CourseOfferingService;
import com.ucms.academic.web.dto.CourseAcademicYearResponse;
import com.ucms.academic.web.dto.CourseResponse;
import com.ucms.academic.web.dto.CreateCourseAcademicYearRequest;
import com.ucms.academic.web.dto.CreateCourseRequest;
import com.ucms.academic.web.dto.UpdateCourseAcademicYearRequest;
import com.ucms.academic.web.dto.UpdateCourseRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseOfferingService courseOfferingService;

    public CourseController(CourseOfferingService courseOfferingService) {
        this.courseOfferingService = courseOfferingService;
    }

    /** mine=true scopes to the caller's own courses (lecturer's "My Courses").
     * degreeProgramId narrows by program — the Courses page's admin filter. */
    @GetMapping
    public List<CourseResponse> list(@RequestParam(name = "mine", required = false, defaultValue = "false") boolean mine,
                                      @RequestParam(name = "degreeProgramId", required = false) UUID degreeProgramId,
                                      @AuthenticationPrincipal UUID currentUserId) {
        return mine ? courseOfferingService.listForLecturer(currentUserId, degreeProgramId)
                    : courseOfferingService.list(degreeProgramId);
    }

    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request,
                                                  @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseOfferingService.create(request, currentUserId));
    }

    @PutMapping("/{courseId}")
    public CourseResponse update(@PathVariable("courseId") UUID courseId,
                                  @Valid @RequestBody UpdateCourseRequest request,
                                  @AuthenticationPrincipal UUID currentUserId) {
        return courseOfferingService.update(courseId, request, currentUserId);
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> delete(@PathVariable("courseId") UUID courseId,
                                        @AuthenticationPrincipal UUID currentUserId) {
        courseOfferingService.delete(courseId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courseId}/academic-years")
    public List<CourseAcademicYearResponse> listAcademicYearAssignments(@PathVariable("courseId") UUID courseId) {
        return courseOfferingService.listAcademicYearAssignments(courseId);
    }

    @PostMapping("/{courseId}/academic-years")
    public ResponseEntity<CourseAcademicYearResponse> assignToAcademicYear(
            @PathVariable("courseId") UUID courseId,
            @Valid @RequestBody CreateCourseAcademicYearRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseOfferingService.assignToAcademicYear(courseId, request, currentUserId));
    }

    @PutMapping("/{courseId}/academic-years/{assignmentId}")
    public CourseAcademicYearResponse updateAcademicYearAssignment(
            @PathVariable("courseId") UUID courseId,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody UpdateCourseAcademicYearRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        return courseOfferingService.updateAcademicYearAssignment(courseId, assignmentId, request, currentUserId);
    }

    @DeleteMapping("/{courseId}/academic-years/{assignmentId}")
    public ResponseEntity<Void> deleteAcademicYearAssignment(
            @PathVariable("courseId") UUID courseId,
            @PathVariable("assignmentId") UUID assignmentId,
            @AuthenticationPrincipal UUID currentUserId) {
        courseOfferingService.deleteAcademicYearAssignment(courseId, assignmentId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
