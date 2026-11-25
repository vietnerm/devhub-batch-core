package com.devhub.batch.api.controller;

import com.devhub.batch.api.dto.MonitorDtos.CronCheckRequest;
import com.devhub.batch.api.dto.MonitorDtos.CronCheckResponse;
import com.devhub.batch.api.service.CronService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cron")
public class CronController {

    private final CronService cronService;

    public CronController(CronService cronService) {
        this.cronService = cronService;
    }

    /** Validates a schedule and previews the next runs (uses the core planner). */
    @PostMapping("/check")
    public CronCheckResponse check(@RequestBody CronCheckRequest request) {
        return cronService.check(request);
    }
}
