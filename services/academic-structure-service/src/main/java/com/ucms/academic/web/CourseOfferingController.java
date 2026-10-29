package com.ucms.academic.web;

import com.ucms.academic.service.CourseOfferingQueryService;
import com.ucms.academic.web.dto.CourseOfferingResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/course-offerings")
public class CourseOfferingController {

    private final CourseOfferingQueryService queryService;

    public CourseOfferingController(CourseOfferingQueryService queryService) {
        this.queryService = queryService;
    }

    /** mine=true narrows to offerings the caller teaches (resolved lecturer). */
    @GetMapping
    public List<CourseOfferingResponse> search(@RequestParam(name = "academicYearId", required = false) UUID academicYearId,
                                                @RequestParam(name = "status", required = false) String status,
                                                @RequestParam(name = "lecturerId", required = false) UUID lecturerId,
                                                @RequestParam(name = "degreeProgramId", required = false) UUID degreeProgramId,
                                                @RequestParam(name = "ids", required = false) List<UUID> ids,
                                                @RequestParam(name = "mine", defaultValue = "false") boolean mine,
                                                @AuthenticationPrincipal UUID currentUserId) {
        return queryService.search(academicYearId, status, mine ? currentUserId : lecturerId, degreeProgramId, ids);
    }

    @GetMapping("/{offeringId}")
    public CourseOfferingResponse get(@PathVariable("offeringId") UUID offeringId) {
        return queryService.get(offeringId);
    }
}
