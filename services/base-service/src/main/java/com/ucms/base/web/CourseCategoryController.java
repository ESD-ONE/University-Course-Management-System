package com.ucms.base.web;

import com.ucms.base.domain.ReferenceStatus;
import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.CreateCourseCategoryRequest;
import com.ucms.base.web.dto.CourseCategoryResponse;
import com.ucms.base.web.dto.ReferenceCounts;
import com.ucms.base.web.dto.UpdateCourseCategoryRequest;
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
@RequestMapping("/course-categories")
public class CourseCategoryController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final BaseService baseService;

    public CourseCategoryController(BaseService baseService) {
        this.baseService = baseService;
    }

    /** status/q are optional and combinable — q is a case-insensitive name/description search. */
    @GetMapping
    public Page<CourseCategoryResponse> list(@RequestParam(name = "status", required = false) ReferenceStatus status,
                                              @RequestParam(name = "q", required = false) String q,
                                              @RequestParam(name = "page", defaultValue = "0") int page,
                                              @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by("name").ascending());
        return baseService.listCourseCategories(status, q, pageable);
    }

    /** Total/active/inactive, independent of the list's current page/filter. */
    @GetMapping("/counts")
    public ReferenceCounts counts() {
        return baseService.courseCategoryCounts();
    }

    @PostMapping
    public ResponseEntity<CourseCategoryResponse> create(@Valid @RequestBody CreateCourseCategoryRequest request,
                                                           @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(baseService.createCourseCategory(request, currentUserId));
    }

    @PutMapping("/{id}")
    public CourseCategoryResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateCourseCategoryRequest request,
                                          @AuthenticationPrincipal UUID currentUserId) {
        return baseService.updateCourseCategory(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        baseService.deleteCourseCategory(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
