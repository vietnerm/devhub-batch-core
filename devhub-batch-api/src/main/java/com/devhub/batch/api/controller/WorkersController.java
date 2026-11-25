package com.devhub.batch.api.controller;

import com.devhub.batch.api.dao.WorkerQueryDao;
import com.devhub.batch.api.dto.MonitorDtos.WorkerDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
public class WorkersController {

    private final WorkerQueryDao workers;

    public WorkersController(WorkerQueryDao workers) {
        this.workers = workers;
    }

    @GetMapping
    public List<WorkerDto> list() {
        return workers.listAll();
    }
}
