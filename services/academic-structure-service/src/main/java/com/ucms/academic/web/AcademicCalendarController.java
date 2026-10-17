package com.ucms.academic.web;

import com.ucms.academic.service.AcademicCalendarService;
import com.ucms.academic.web.dto.AcademicYearResponse;
import com.ucms.academic.web.dto.CreateAcademicYearRequest;
import com.ucms.academic.web.dto.CreateSemesterRequest;
import com.ucms.academic.web.dto.SemesterResponse;
import com.ucms.academic.web.dto.UpdateAcademicYearRequest;
import com.ucms.academic.web.dto.UpdateSemesterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AcademicCalendarController {

    private final AcademicCalendarService academicCalendarService;

    public AcademicCalendarController(AcademicCalendarService academicCalendarService) {
        this.academicCalendarService = academicCalendarService;
    }

    @GetMapping("/academic-years")
    public List<AcademicYearResponse> list() {
        return academicCalendarService.listYears();
    }

    @PostMapping("/academic-years")
    public ResponseEntity<AcademicYearResponse> create(@Valid @RequestBody CreateAcademicYearRequest request,
                                                         @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academicCalendarService.createYear(request, currentUserId));
    }

    @PostMapping("/academic-years/{yearId}/semesters")
    public ResponseEntity<SemesterResponse> addSemester(@PathVariable("yearId") UUID yearId,
                                                          @Valid @RequestBody CreateSemesterRequest request,
                                                          @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academicCalendarService.addSemester(yearId, request, currentUserId));
    }

    @PatchMapping("/academic-years/{yearId}/activate")
    public AcademicYearResponse activate(@PathVariable("yearId") UUID yearId,
                                          @AuthenticationPrincipal UUID currentUserId) {
        return academicCalendarService.activate(yearId, currentUserId);
    }

    @PutMapping("/academic-years/{yearId}")
    public AcademicYearResponse update(@PathVariable("yearId") UUID yearId,
                                        @Valid @RequestBody UpdateAcademicYearRequest request,
                                        @AuthenticationPrincipal UUID currentUserId) {
        return academicCalendarService.updateYear(yearId, request, currentUserId);
    }

    @DeleteMapping("/academic-years/{yearId}")
    public ResponseEntity<Void> delete(@PathVariable("yearId") UUID yearId,
                                        @AuthenticationPrincipal UUID currentUserId) {
        academicCalendarService.deleteYear(yearId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/academic-years/{yearId}/semesters/{semesterId}")
    public SemesterResponse updateSemester(@PathVariable("yearId") UUID yearId,
                                            @PathVariable("semesterId") UUID semesterId,
                                            @Valid @RequestBody UpdateSemesterRequest request,
                                            @AuthenticationPrincipal UUID currentUserId) {
        return academicCalendarService.updateSemester(yearId, semesterId, request, currentUserId);
    }

    @DeleteMapping("/academic-years/{yearId}/semesters/{semesterId}")
    public ResponseEntity<Void> deleteSemester(@PathVariable("yearId") UUID yearId,
                                                @PathVariable("semesterId") UUID semesterId,
                                                @AuthenticationPrincipal UUID currentUserId) {
        academicCalendarService.deleteSemester(yearId, semesterId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
