package com.example.materialstudio.service;

import com.example.materialstudio.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskTransitionService {
    private final TaskMapper tasks;

    public TaskTransitionService(TaskMapper tasks) {
        this.tasks = tasks;
    }

    @Transactional
    public void begin(String taskId, String stage) {
        if (tasks.setStage(taskId, stage) != 1) throw new IllegalStateException("Task is not running");
        tasks.startEvent(taskId, stage);
    }

    @Transactional
    public void complete(String taskId, String stage) {
        if (tasks.completeEvent(taskId, stage) != 1) throw new IllegalStateException("Stage is not running");
    }

    @Transactional
    public void finish(String taskId, String resultPath) {
        if (tasks.completeEvent(taskId, "FINALIZE") != 1) throw new IllegalStateException("Final stage is not running");
        if (tasks.finish(taskId, resultPath) != 1) throw new IllegalStateException("Task cannot finish");
    }

    @Transactional
    public void fail(String taskId, String stage, String reason) {
        tasks.failEvent(taskId, stage, reason);
        tasks.fail(taskId, stage, reason);
    }
}
