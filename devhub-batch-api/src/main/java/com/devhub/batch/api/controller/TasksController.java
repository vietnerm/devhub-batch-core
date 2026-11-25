package com.devhub.batch.api.controller;

import com.devhub.batch.api.dao.TaskQueryDao;
import com.devhub.batch.api.dto.TaskDtos.CreateTaskRequest;
import com.devhub.batch.api.dto.TaskDtos.TaskDetail;
import com.devhub.batch.api.dto.TaskDtos.TaskDto;
import com.devhub.batch.api.dto.TaskDtos.TaskPage;
import com.devhub.batch.api.dto.TaskDtos.UpdateTaskRequest;
import com.devhub.batch.api.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TasksController {

    private final TaskService taskService;
    private final TaskQueryDao queryDao;

    public TasksController(TaskService taskService, TaskQueryDao queryDao) {
        this.taskService = taskService;
        this.queryDao = queryDao;
    }

    @GetMapping
    public TaskPage list(@RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int size,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) String sort,
                         @RequestParam(required = false) String dir) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        return queryDao.page(status, q, safePage, safeSize, sort, dir);
    }

    @GetMapping("/{id}")
    public TaskDetail detail(@PathVariable Long id) {
        return taskService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TaskDto create(@RequestBody CreateTaskRequest request) {
        return TaskDto.fromModel(taskService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskDto update(@PathVariable Long id, @RequestBody UpdateTaskRequest request) {
        return TaskDto.fromModel(taskService.update(id, request));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskDto pause(@PathVariable Long id) {
        return TaskDto.fromModel(taskService.pause(id));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskDto resume(@PathVariable Long id) {
        return TaskDto.fromModel(taskService.resume(id));
    }

    @PostMapping("/{id}/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public TaskDto triggerNow(@PathVariable Long id) {
        return TaskDto.fromModel(taskService.triggerNow(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Map.of("deleted", id);
    }
}
