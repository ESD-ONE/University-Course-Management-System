package com.ucms.enrollment.web;

import com.ucms.enrollment.security.Caller;
import com.ucms.enrollment.service.CourseEnrollmentService;
import com.ucms.enrollment.web.dto.Dtos.CatalogEntryResponse;
import com.ucms.enrollment.web.dto.Dtos.CourseEnrollmentResponse;
import com.ucms.enrollment.web.dto.Dtos.EnrollRequest;
import com.ucms.enrollment.web.dto.Dtos.EnrollmentCheckResponse;
import com.ucms.enrollment.web.dto.Dtos.MyStudentResponse;
import com.ucms.enrollment.web.dto.Dtos.SeatCountResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CourseEnrollmentController {

    private final CourseEnrollmentService service;

    public CourseEnrollmentController(CourseEnrollmentService service) {
        this.service = service;
    }

    @PostMapping("/course-enrollments")
    public ResponseEntity<CourseEnrollmentResponse> enroll(@Valid @RequestBody EnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.enroll(request, Caller.current()));
    }

    @DeleteMapping("/course-enrollments/{id}")
    public ResponseEntity<Void> drop(@PathVariable("id") UUID id) {
        service.drop(id, Caller.current());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/course-enrollments/me")
    public List<CourseEnrollmentResponse> mine() {
        return service.mine(Caller.current());
    }

    @GetMapping("/course-enrollments")
    public List<CourseEnrollmentResponse> roster(@RequestParam(name = "offeringId") UUID offeringId) {
        return service.roster(offeringId, Caller.current());
    }

    @GetMapping("/course-enrollments/my-students")
    public List<MyStudentResponse> myStudents() {
        return service.myStudents(Caller.current());
    }

    @GetMapping("/course-enrollments/check")
    public EnrollmentCheckResponse check(@RequestParam(name = "studentId") UUID studentId,
                                         @RequestParam(name = "offeringId") UUID offeringId) {
        return service.check(studentId, offeringId);
    }

    @GetMapping("/offerings/seats")
    public List<SeatCountResponse> seats(@RequestParam(name = "ids") List<UUID> ids) {
        return service.seats(ids);
    }

    @GetMapping("/catalog")
    public List<CatalogEntryResponse> catalog(@RequestParam(name = "academicYearId", required = false) UUID academicYearId) {
        return service.catalog(academicYearId, Caller.current());
    }
}
