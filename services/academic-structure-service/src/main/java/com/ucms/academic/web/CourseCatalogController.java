package com.ucms.academic.web;

import com.ucms.academic.service.CourseCatalogService;
import com.ucms.academic.web.dto.CourseCatalogResponse;
import com.ucms.academic.web.dto.CreateCourseCatalogRequest;
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

    @GetMapping
    public List<CourseCatalogResponse> list() {
        return courseCatalogService.list();
    }

    @PostMapping
    public ResponseEntity<CourseCatalogResponse> create(@Valid @RequestBody CreateCourseCatalogRequest request,
                                                          @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseCatalogService.create(request, currentUserId));
    }
}
