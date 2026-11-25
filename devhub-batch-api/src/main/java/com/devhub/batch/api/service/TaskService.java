package com.devhub.batch.api.service;

import com.devhub.batch.api.dao.LogQueryDao;
import com.devhub.batch.api.dto.MonitorDtos.LogDto;
import com.devhub.batch.api.dto.TaskDtos.CreateTaskRequest;
import com.devhub.batch.api.dto.TaskDtos.TaskDetail;
import com.devhub.batch.api.dto.TaskDtos.TaskDto;
import com.devhub.batch.api.dto.TaskDtos.UpdateTaskRequest;
import com.devhub.batch.core.model.ScheduledTask;
import com.devhub.batch.core.repository.JdbcTaskRepository.RepositoryException;
import com.devhub.batch.core.repository.TaskRepository;
import com.devhub.batch.core.schedule.SchedulePlanner;
import org.springframework.stereotype.Service;

import com.devhub.batch.core.support.Times;
import java.time.LocalDateTime;

/**
 * All writes flow through the core {@link TaskRepository}: schedule validation,
 * NEXT_RUN_AT planning (Y5) and status transitions stay owned by the engine.
 * The API never issues its own UPDATE against SYS_SCHEDULED_TASKS.
 */
@Service
public class TaskService {

    private final TaskRepository repo;
    private final LogQueryDao logs;

    public TaskService(TaskRepository repo, LogQueryDao logs) {
        this.repo = repo;
        this.logs = logs;
    }

    public TaskDetail detail(Long id) {
        ScheduledTask task = require(id);
        return new TaskDetail(TaskDto.fromModel(task), logs.findByTask(id, 20));
    }

    public ScheduledTask create(CreateTaskRequest r) {
        ScheduledTask task = new ScheduledTask();
        task.setFunctionCode(r.functionCode());
        task.setFunctionName(r.functionName());
        task.setTaskName(required(r.taskName(), "taskName is required (TASK_NAME)"));
        task.setBeanClass(required(r.beanClass(), "beanClass is required (BEAN_CLASS)"));
        task.setScheduleType(r.scheduleType());
        task.setCronExpression(r.cronExpression());
        task.setScheduledTime(r.scheduledTime());
        task.setStartAt(r.startAt());
        task.setEndAt(r.endAt());
        task.setTimezone(r.timezone());
        task.setMisfirePolicy(r.misfirePolicy());
        task.setPriority(r.priority());
        task.setDescription(r.description());
        task.setRequeueOnFailure(Boolean.TRUE.equals(r.requeueOnFailure()));
        task.setTimeoutSeconds(r.timeoutSeconds());
        task.setStatus("SCHEDULED");
        try {
            return repo.save(task);
        } catch (IllegalArgumentException invalid) {
            throw ApiException.badRequest(invalid.getMessage());
        }
    }

    public ScheduledTask update(Long id, UpdateTaskRequest r) {
        ScheduledTask task = require(id);
        if (task.getStatus() != null && "RUNNING".equals(task.getStatus())) {
            throw ApiException.conflict("Không thể sửa task đang chạy (task is RUNNING — pause or wait first)");
        }
        if (r.functionCode() != null) task.setFunctionCode(r.functionCode());
        if (r.functionName() != null) task.setFunctionName(r.functionName());
        if (r.taskName() != null) task.setTaskName(required(r.taskName(), "taskName must not be blank"));
        if (r.beanClass() != null) task.setBeanClass(required(r.beanClass(), "beanClass must not be blank"));
        if (r.scheduleType() != null) task.setScheduleType(r.scheduleType());
        if (r.cronExpression() != null) task.setCronExpression(r.cronExpression());
        if (r.scheduledTime() != null) task.setScheduledTime(r.scheduledTime());
        if (r.startAt() != null) task.setStartAt(r.startAt());
        if (r.endAt() != null) task.setEndAt(r.endAt());
        if (r.timezone() != null) task.setTimezone(r.timezone());
        if (r.misfirePolicy() != null) task.setMisfirePolicy(r.misfirePolicy());
        if (r.priority() != null) task.setPriority(r.priority());
        if (r.description() != null) task.setDescription(r.description());
        if (r.requeueOnFailure() != null) task.setRequeueOnFailure(r.requeueOnFailure());
        if (r.timeoutSeconds() != null) task.setTimeoutSeconds(r.timeoutSeconds());
        try {
            repo.save(task);
        } catch (IllegalArgumentException invalid) {
            throw ApiException.badRequest(invalid.getMessage());
        }
        // A schedule change invalidates the stored NEXT_RUN_AT — re-plan it while
        // the row is SCHEDULED (updateNextRunAt is guarded by STATUS='SCHEDULED').
        if ("SCHEDULED".equals(task.getStatus())) {
            repo.updateNextRunAt(id, SchedulePlanner.initialNextRun(task, Times.nowUtc()));
        }
        return require(id);
    }

    public ScheduledTask pause(Long id) {
        ScheduledTask task = require(id);
        if (!"SCHEDULED".equals(task.getStatus())) {
            throw ApiException.conflict("Chỉ pause được task ở trạng thái SCHEDULED (current: " + task.getStatus() + ")");
        }
        repo.updateStatus(id, "PAUSED");
        return require(id);
    }

    public ScheduledTask resume(Long id) {
        ScheduledTask task = require(id);
        if (!"PAUSED".equals(task.getStatus())) {
            throw ApiException.conflict("Chỉ resume được task ở trạng thái PAUSED (current: " + task.getStatus() + ")");
        }
        repo.updateStatus(id, "SCHEDULED");
        ScheduledTask reloaded = require(id);
        if (reloaded.getNextRunAt() == null || reloaded.getNextRunAt().isBefore(Times.nowUtc())) {
            repo.updateNextRunAt(id, SchedulePlanner.initialNextRun(reloaded, Times.nowUtc()));
        }
        return require(id);
    }

    /** Trigger now = move NEXT_RUN_AT to the current instant (Y5-safe: the
     *  dispatch loop claims it atomically within one poll interval). */
    public ScheduledTask triggerNow(Long id) {
        ScheduledTask task = require(id);
        if (!"SCHEDULED".equals(task.getStatus())) {
            throw ApiException.conflict("Chỉ trigger ngay được task ở trạng thái SCHEDULED (current: " + task.getStatus() + ")");
        }
        repo.updateNextRunAt(id, Times.nowUtc());
        return require(id);
    }

    public void delete(Long id) {
        ScheduledTask task = require(id);
        if ("RUNNING".equals(task.getStatus())) {
            throw ApiException.conflict("Không thể xóa task đang chạy (wait for it to finish or timeout)");
        }
        try {
            repo.delete(id);
        } catch (RepositoryException e) {
            throw new ApiException(500, e.getMessage());
        }
    }

    private ScheduledTask require(Long id) {
        ScheduledTask task = repo.findById(id);
        if (task == null) throw ApiException.notFound("Không tìm thấy task #" + id);
        return task;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(message);
        return value.trim();
    }
}
