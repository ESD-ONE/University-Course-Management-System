package com.ucms.base.web;

import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.CreateCourseCategoryRequest;
import com.ucms.base.web.dto.CourseCategoryResponse;
import com.ucms.base.web.dto.UpdateCourseCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/course-categories")
public class CourseCategoryController {

    private final BaseService baseService;

    public CourseCategoryController(BaseService baseService) {
        this.baseService = baseService;
    }

    @GetMapping
    public List<CourseCategoryResponse> list() {
        return baseService.listCourseCategories();
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
