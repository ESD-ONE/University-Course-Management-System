package com.ucms.base.web;

import com.ucms.base.service.BaseService;
import com.ucms.base.web.dto.CreateGradingScaleRequest;
import com.ucms.base.web.dto.GradingScaleResponse;
import com.ucms.base.web.dto.UpdateGradingScaleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/grading-scales")
public class GradingScaleController {

    private final BaseService baseService;

    public GradingScaleController(BaseService baseService) {
        this.baseService = baseService;
    }

    @GetMapping
    public List<GradingScaleResponse> list() {
        return baseService.listGradingScales();
    }

    @PostMapping
    public ResponseEntity<GradingScaleResponse> create(@Valid @RequestBody CreateGradingScaleRequest request,
                                                         @AuthenticationPrincipal UUID currentUserId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(baseService.createGradingScale(request, currentUserId));
    }

    @PutMapping("/{id}")
    public GradingScaleResponse update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateGradingScaleRequest request,
                                        @AuthenticationPrincipal UUID currentUserId) {
        return baseService.updateGradingScale(id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal UUID currentUserId) {
        baseService.deleteGradingScale(id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
