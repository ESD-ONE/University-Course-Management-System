package com.ucms.enrollment.web;

import com.ucms.enrollment.domain.TargetType;
import com.ucms.enrollment.security.Caller;
import com.ucms.enrollment.service.RegistrationQueryService;
import com.ucms.enrollment.web.dto.Dtos.RegistrationResponse;
import com.ucms.enrollment.web.dto.Dtos.StatsResponse;
import com.ucms.enrollment.web.dto.Dtos.StudentOption;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** studentId/targetType/action are all optional and combinable. studentId narrows to
     * one student's history; otherwise an admin sees every registration and anyone else
     * sees only the ones they personally made. */
    @GetMapping("/registrations")
    public Page<RegistrationResponse> recent(@RequestParam(name = "page", defaultValue = "0") int page,
                                              @RequestParam(name = "size", defaultValue = "20") int size,
                                              @RequestParam(name = "studentId", required = false) UUID studentId,
                                              @RequestParam(name = "targetType", required = false) TargetType targetType,
                                              @RequestParam(name = "action", required = false) String action) {
        return service.recent(page, size, studentId, targetType, action, Caller.current());
    }

    @DeleteMapping("/registrations/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        service.delete(id, Caller.current());
        return ResponseEntity.noContent().build();
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
