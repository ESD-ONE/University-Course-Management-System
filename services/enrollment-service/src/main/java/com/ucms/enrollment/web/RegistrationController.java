package com.ucms.enrollment.web;

import com.ucms.enrollment.security.Caller;
import com.ucms.enrollment.service.RegistrationQueryService;
import com.ucms.enrollment.web.dto.Dtos.RegistrationResponse;
import com.ucms.enrollment.web.dto.Dtos.StatsResponse;
import com.ucms.enrollment.web.dto.Dtos.StudentOption;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class RegistrationController {

    private final RegistrationQueryService service;

    public RegistrationController(RegistrationQueryService service) {
        this.service = service;
    }

    @GetMapping("/registrations")
    public List<RegistrationResponse> recent(@RequestParam(name = "limit", defaultValue = "20") int limit,
                                             @RequestParam(name = "studentId", required = false) UUID studentId) {
        return service.recent(limit, studentId, Caller.current());
    }

    @GetMapping("/students")
    public List<StudentOption> students(@RequestParam(name = "q", required = false) String q) {
        return service.searchStudents(q);
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return service.stats();
    }
}
