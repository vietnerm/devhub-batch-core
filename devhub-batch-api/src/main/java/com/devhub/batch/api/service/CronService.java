package com.devhub.batch.api.service;

import com.devhub.batch.api.dto.MonitorDtos.CronCheckRequest;
import com.devhub.batch.api.dto.MonitorDtos.CronCheckResponse;
import com.devhub.batch.core.cron.CronCalculator;
import com.devhub.batch.core.schedule.ScheduleValidator;
import org.springframework.stereotype.Service;

import com.devhub.batch.core.support.Times;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cron/timezone checker for the create-task dialog: reuses the core validator
 * (Spring 6-field format, fix Y2 timezone rules) and previews the next runs
 * with the exact planner used by the engine.
 */
@Service
public class CronService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DEFAULT_TZ = "Asia/Ho_Chi_Minh";

    public CronCheckResponse check(CronCheckRequest request) {
        String type = ScheduleValidator.normalizeType(request.scheduleType(),
                request.cronExpression(), request.scheduledTime());
        List<String> nextRuns = new ArrayList<>();

        ScheduleValidator.Result result = ScheduleValidator.validate(type,
                request.cronExpression(), request.scheduledTime(), null, null, null);
        if (!result.valid()) {
            return new CronCheckResponse(false, result.message(), result.scheduleType(), nextRuns);
        }
        String tzError = ScheduleValidator.validateTimezone(request.timezone());
        if (tzError != null) {
            return new CronCheckResponse(false, tzError, result.scheduleType(), nextRuns);
        }

        String tz = request.timezone() == null || request.timezone().isBlank() ? DEFAULT_TZ : request.timezone().trim();
        try {
            if (ScheduleValidator.ONCE.equals(result.scheduleType())) {
                nextRuns.add(ISO.format(request.scheduledTime()));
            } else {
                ZoneId zone = CronCalculator.zoneOf(tz);
                LocalDateTime cursor = Times.nowUtc();
                for (int i = 0; i < 3; i++) {
                    cursor = CronCalculator.nextExecution(request.cronExpression().trim(), zone, cursor);
                    nextRuns.add(ISO.format(cursor));
                }
            }
        } catch (Exception e) {
            return new CronCheckResponse(false, "Cannot compute next runs: " + e.getMessage(),
                    result.scheduleType(), nextRuns);
        }
        return new CronCheckResponse(true, "", result.scheduleType(), nextRuns);
    }
}
