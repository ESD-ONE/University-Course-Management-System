package com.ucms.enrollment.web;

import com.ucms.enrollment.security.Caller;
import com.ucms.enrollment.service.ProgramEnrollmentService;
import com.ucms.enrollment.web.dto.Dtos.CreateProgramEnrollmentRequest;
import com.ucms.enrollment.web.dto.Dtos.ProgramEnrollmentResponse;
import com.ucms.enrollment.web.dto.Dtos.UpdateProgramEnrollmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/program-enrollments")
public class ProgramEnrollmentController {

    private final ProgramEnrollmentService service;

    public ProgramEnrollmentController(ProgramEnrollmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProgramEnrollmentResponse> register(@Valid @RequestBody CreateProgramEnrollmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request, Caller.current()));
    }

    @GetMapping
    public List<ProgramEnrollmentResponse> list(@RequestParam(name = "studentId", required = false) UUID studentId,
                                                @RequestParam(name = "degreeProgramId", required = false) UUID degreeProgramId) {
        return service.list(studentId, degreeProgramId);
    }

    @GetMapping("/me")
    public List<ProgramEnrollmentResponse> mine() {
        return service.mine(Caller.current());
    }

    @PatchMapping("/{id}")
    public ProgramEnrollmentResponse update(@PathVariable("id") UUID id,
                                            @Valid @RequestBody UpdateProgramEnrollmentRequest request) {
        return service.update(id, request, Caller.current());
    }
}
