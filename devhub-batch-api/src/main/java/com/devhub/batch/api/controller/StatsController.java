package com.devhub.batch.api.controller;

import com.devhub.batch.api.dto.MonitorDtos.LogDto;
import com.devhub.batch.api.dto.MonitorDtos.StatsSummary;
import com.devhub.batch.api.dto.MonitorDtos.TimelinePoint;
import com.devhub.batch.api.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    public StatsSummary summary() {
        return statsService.summary();
    }

    @GetMapping("/timeline")
    public List<TimelinePoint> timeline(@RequestParam(defaultValue = "24") int hours) {
        return statsService.timeline(hours);
    }

    @GetMapping("/latest-failure")
    public LogDto latestFailure() {
        return statsService.latestFailure();
    }
}
