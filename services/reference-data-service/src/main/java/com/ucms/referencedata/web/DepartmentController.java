package com.ucms.referencedata.web;

import com.ucms.referencedata.service.ReferenceDataService;
import com.ucms.referencedata.web.dto.CreateDepartmentRequest;
import com.ucms.referencedata.web.dto.DepartmentResponse;
import com.ucms.referencedata.web.dto.UpdateDepartmentRequest;
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

    private final ReferenceDataService referenceDataService;

    public DepartmentController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public List<DepartmentResponse> list(@RequestParam(name = "facultyId", required = false) UUID facultyId) {
        return referenceDataService.listDepartments(facultyId);
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request,
                                                       @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceDataService.createDepartment(request, currentUserId));
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request,
                                      @AuthenticationPrincipal UUID currentUserId) {
        return referenceDataService.updateDepartment(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal UUID currentUserId) {
        referenceDataService.deleteDepartment(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
