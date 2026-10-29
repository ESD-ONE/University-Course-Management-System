package com.ucms.academic.web;

import com.ucms.academic.service.CourseCatalogService;
import com.ucms.academic.web.dto.CourseCatalogResponse;
import com.ucms.academic.web.dto.CreateCourseCatalogRequest;
import com.ucms.academic.web.dto.UpdateCourseCatalogRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/course-catalog")
public class CourseCatalogController {

    private final CourseCatalogService courseCatalogService;

    public CourseCatalogController(CourseCatalogService courseCatalogService) {
        this.courseCatalogService = courseCatalogService;
    }

    /** departmentId: optional filter on owningDepartmentId, mirroring
     * GET /degree-programs?departmentId= — used by base-service's parallel
     * G-05 fix. Omitted => every entry, same as before. */
    @GetMapping
    public List<CourseCatalogResponse> list(@RequestParam(name = "departmentId", required = false) UUID departmentId) {
        return courseCatalogService.list(departmentId);
    }

    @PostMapping
    public ResponseEntity<CourseCatalogResponse> create(@Valid @RequestBody CreateCourseCatalogRequest request,
                                                          @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseCatalogService.create(request, currentUserId));
    }

    @PutMapping("/{id}")
    public CourseCatalogResponse update(@PathVariable("id") UUID id,
                                         @Valid @RequestBody UpdateCourseCatalogRequest request,
                                         @AuthenticationPrincipal UUID currentUserId) {
        return courseCatalogService.update(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id,
                                        @AuthenticationPrincipal UUID currentUserId) {
        courseCatalogService.delete(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
