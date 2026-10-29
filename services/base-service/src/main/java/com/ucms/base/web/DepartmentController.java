package com.ucms.base.web;

import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.CreateDepartmentRequest;
import com.ucms.base.web.dto.DepartmentResponse;
import com.ucms.base.web.dto.UpdateDepartmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private final BaseService baseService;

    public DepartmentController(BaseService baseService) {
        this.baseService = baseService;
    }

    @GetMapping
    public List<DepartmentResponse> list(@RequestParam(name = "facultyId", required = false) UUID facultyId) {
        return baseService.listDepartments(facultyId);
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request,
                                                       @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(baseService.createDepartment(request, currentUserId));
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateDepartmentRequest request,
                                      @AuthenticationPrincipal UUID currentUserId) {
        return baseService.updateDepartment(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        baseService.deleteDepartment(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
