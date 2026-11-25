package com.ucms.grading.web;

import com.ucms.grading.security.Caller;
import com.ucms.grading.service.UpcomingService;
import com.ucms.grading.web.dto.Dtos.UpcomingItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** One merged, role-scoped "what's coming up" feed — lecture sessions starting soon and
 * assignments due soon — for the three role dashboards. See
 * docs/ASSIGNMENT_WEEK_LINKING_AND_DASHBOARD_ARCHITECTURE.md section 3.2. */
@RestController
public class UpcomingController {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 30;

    private final UpcomingService upcoming;

    public UpcomingController(UpcomingService upcoming) {
        this.upcoming = upcoming;
    }

    @GetMapping("/dashboard/upcoming")
    public List<UpcomingItem> upcoming(@RequestParam(name = "days", defaultValue = "" + DEFAULT_DAYS) int days) {
        return upcoming.upcoming(Math.min(Math.max(days, 1), MAX_DAYS), Caller.current());
    }
}
