package com.ucms.academic.web;

import com.ucms.academic.domain.ProgramStatus;
import com.ucms.academic.service.DegreeProgramService;
import com.ucms.academic.web.dto.CreateDegreeProgramAcademicYearRequest;
import com.ucms.academic.web.dto.CreateDegreeProgramRequest;
import com.ucms.academic.web.dto.CreateProgramCourseRequest;
import com.ucms.academic.web.dto.DegreeProgramAcademicYearResponse;
import com.ucms.academic.web.dto.DegreeProgramResponse;
import com.ucms.academic.web.dto.ProgramCourseResponse;
import com.ucms.academic.web.dto.UpdateDegreeProgramAcademicYearRequest;
import com.ucms.academic.web.dto.UpdateDegreeProgramRequest;
import com.ucms.academic.web.dto.UpdateProgramCourseRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class DegreeProgramController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final DegreeProgramService degreeProgramService;

    public DegreeProgramController(DegreeProgramService degreeProgramService) {
        this.degreeProgramService = degreeProgramService;
    }

    /** departmentId/status/q are all optional and combinable — departmentId is the
     * "Reference Degree Details" page's admin department filter, q a case-insensitive
     * name/code search. */
    @GetMapping("/degree-programs")
    public Page<DegreeProgramResponse> list(@RequestParam(name = "departmentId", required = false) UUID departmentId,
                                             @RequestParam(name = "status", required = false) ProgramStatus status,
                                             @RequestParam(name = "q", required = false) String q,
                                             @RequestParam(name = "page", defaultValue = "0") int page,
                                             @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("name").ascending());
        return degreeProgramService.list(departmentId, status, q, pageable);
    }

    @GetMapping("/degree-programs/{programId}")
    public DegreeProgramResponse get(@PathVariable("programId") UUID programId) {
        return degreeProgramService.get(programId);
    }

    @PostMapping("/degree-programs")
    public ResponseEntity<DegreeProgramResponse> create(@Valid @RequestBody CreateDegreeProgramRequest request,
                                                          @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(degreeProgramService.create(request, currentUserId));
    }

    @PutMapping("/degree-programs/{programId}")
    public DegreeProgramResponse update(@PathVariable("programId") UUID programId,
                                         @Valid @RequestBody UpdateDegreeProgramRequest request,
                                         @AuthenticationPrincipal UUID currentUserId) {
        return degreeProgramService.update(programId, request, currentUserId);
    }

    @DeleteMapping("/degree-programs/{programId}")
    public ResponseEntity<Void> delete(@PathVariable("programId") UUID programId,
                                        @AuthenticationPrincipal UUID currentUserId) {
        degreeProgramService.delete(programId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/degree-programs/{programId}/curriculum")
    public List<ProgramCourseResponse> listCurriculum(@PathVariable("programId") UUID programId) {
        return degreeProgramService.listCurriculum(programId);
    }

    @PostMapping("/degree-programs/{programId}/curriculum")
    public ResponseEntity<ProgramCourseResponse> addCurriculum(@PathVariable("programId") UUID programId,
                                                                 @Valid @RequestBody CreateProgramCourseRequest request,
                                                                 @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(degreeProgramService.addCurriculum(programId, request, currentUserId));
    }

    @PutMapping("/degree-programs/{programId}/curriculum/{programCourseId}")
    public ProgramCourseResponse updateCurriculum(@PathVariable("programId") UUID programId,
                                                   @PathVariable("programCourseId") UUID programCourseId,
                                                   @Valid @RequestBody UpdateProgramCourseRequest request,
                                                   @AuthenticationPrincipal UUID currentUserId) {
        return degreeProgramService.updateCurriculumEntry(programId, programCourseId, request, currentUserId);
    }

    /** academicYearId: the "Degree Programs" screen's year filter — which reference
     * degrees are assigned/continuing for that year. Omitted => every year ("All Years"),
     * which is when the Academic Year column on that screen actually matters. */
    @GetMapping("/degree-program-academic-years")
    public List<DegreeProgramAcademicYearResponse> listForAcademicYear(
            @RequestParam(name = "academicYearId", required = false) UUID academicYearId) {
        return degreeProgramService.listForAcademicYear(academicYearId);
    }

    @PostMapping("/degree-programs/{programId}/academic-years")
    public ResponseEntity<DegreeProgramAcademicYearResponse> assignToAcademicYear(
            @PathVariable("programId") UUID programId,
            @Valid @RequestBody CreateDegreeProgramAcademicYearRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(degreeProgramService.assignToAcademicYear(programId, request, currentUserId));
    }

    @PutMapping("/degree-programs/{programId}/academic-years/{assignmentId}")
    public DegreeProgramAcademicYearResponse updateAssignment(
            @PathVariable("programId") UUID programId,
            @PathVariable("assignmentId") UUID assignmentId,
            @Valid @RequestBody UpdateDegreeProgramAcademicYearRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        return degreeProgramService.updateAssignment(programId, assignmentId, request, currentUserId);
    }

    @DeleteMapping("/degree-programs/{programId}/academic-years/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable("programId") UUID programId,
            @PathVariable("assignmentId") UUID assignmentId,
            @AuthenticationPrincipal UUID currentUserId) {
        degreeProgramService.deleteAssignment(programId, assignmentId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    /** The "Degree Programs" screen's per-row collapse/expand — this assignment's
     * courses for that academic year, each with its resolved lecturer. */
    @GetMapping("/degree-programs/{programId}/academic-years/{assignmentId}/courses")
    public List<com.ucms.academic.web.dto.CourseAcademicYearResponse> listCoursesForAssignment(
            @PathVariable("programId") UUID programId,
            @PathVariable("assignmentId") UUID assignmentId) {
        return degreeProgramService.listCoursesForAssignment(programId, assignmentId);
    }
}
