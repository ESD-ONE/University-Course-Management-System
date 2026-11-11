package com.ucms.academic.web;

import com.ucms.academic.service.CourseCatalogService;
import com.ucms.academic.web.dto.CourseCatalogResponse;
import com.ucms.academic.web.dto.CreateCourseCatalogRequest;
import com.ucms.academic.web.dto.UpdateCourseCatalogRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/course-catalog")
public class CourseCatalogController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CourseCatalogService courseCatalogService;

    public CourseCatalogController(CourseCatalogService courseCatalogService) {
        this.courseCatalogService = courseCatalogService;
    }

    /** departmentId/q are optional and combinable — departmentId mirrors
     * GET /degree-programs?departmentId= (used by base-service's parallel G-05 fix),
     * q is a case-insensitive name/code search. Omitted => every entry, paginated. */
    @GetMapping
    public Page<CourseCatalogResponse> list(@RequestParam(name = "departmentId", required = false) UUID departmentId,
                                             @RequestParam(name = "q", required = false) String q,
                                             @RequestParam(name = "page", defaultValue = "0") int page,
                                             @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("code").ascending());
        return courseCatalogService.list(departmentId, q, pageable);
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
