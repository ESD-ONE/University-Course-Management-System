package com.ucms.referencedata.web;

import com.ucms.referencedata.service.ReferenceDataService;
import com.ucms.referencedata.web.dto.CreateCourseCategoryRequest;
import com.ucms.referencedata.web.dto.CourseCategoryResponse;
import com.ucms.referencedata.web.dto.UpdateCourseCategoryRequest;
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

    private final ReferenceDataService referenceDataService;

    public CourseCategoryController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping
    public List<CourseCategoryResponse> list() {
        return referenceDataService.listCourseCategories();
    }

    @PostMapping
    public ResponseEntity<CourseCategoryResponse> create(@Valid @RequestBody CreateCourseCategoryRequest request,
                                                           @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceDataService.createCourseCategory(request, currentUserId));
    }

    @PutMapping("/{id}")
    public CourseCategoryResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateCourseCategoryRequest request,
                                          @AuthenticationPrincipal UUID currentUserId) {
        return referenceDataService.updateCourseCategory(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        referenceDataService.deleteCourseCategory(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
