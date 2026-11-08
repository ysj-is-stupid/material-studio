package com.example.materialstudio.web;

import com.example.materialstudio.model.TaskRow;
import com.example.materialstudio.service.IdentityService;
import com.example.materialstudio.service.StorageService;
import com.example.materialstudio.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {
    private final IdentityService identity;
    private final TaskService tasks;
    private final StorageService storage;

    public TaskController(IdentityService identity, TaskService tasks, StorageService storage) {
        this.identity = identity;
        this.tasks = tasks;
        this.storage = storage;
    }

    @PostMapping("/api/tasks")
    public ResponseEntity<Map<String, String>> submit(HttpServletRequest request,
                                                       @RequestBody TaskService.SubmitRequest body) {
        String id = tasks.submit(identity.requireViewerId(request), body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("taskId", id, "status", "QUEUED"));
    }

    @GetMapping("/api/tasks")
    public TaskPage history(HttpServletRequest request) {
        List<TaskService.TaskView> items = tasks.history(identity.requireViewerId(request));
        return new TaskPage(items, null);
    }

    @GetMapping("/api/tasks/{id}")
    public TaskService.TaskView detail(HttpServletRequest request, @PathVariable String id) {
        return tasks.get(identity.requireViewerId(request), id);
    }

    @GetMapping("/api/tasks/{id}/result")
    public ResponseEntity<Resource> result(HttpServletRequest request, @PathVariable String id) {
        TaskRow task = tasks.owned(identity.requireViewerId(request), id);
        if (!"SUCCEEDED".equals(task.status) || task.resultPath == null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESULT_NOT_READY", "结果尚未生成");
        }
        var path = storage.resolve(task.resultPath);
        if (!Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESULT_NOT_FOUND", "结果文件不存在");
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(new FileSystemResource(path));
    }

    public record TaskPage(List<TaskService.TaskView> items, String nextCursor) {}
}
