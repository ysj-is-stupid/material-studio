package com.example.materialstudio.service;

import com.example.materialstudio.mapper.AssetMapper;
import com.example.materialstudio.mapper.TaskMapper;
import com.example.materialstudio.mapper.TemplateMapper;
import com.example.materialstudio.model.AssetRow;
import com.example.materialstudio.model.StageEventRow;
import com.example.materialstudio.model.TaskRow;
import com.example.materialstudio.model.TemplateRow;
import com.example.materialstudio.web.ApiException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final TaskMapper tasks;
    private final TemplateMapper templates;
    private final AssetMapper assets;
    private final StorageService storage;
    private final String defaultFailStage;
    private final int defaultStepDelayMs;
    private final boolean allowOverride;

    public TaskService(TaskMapper tasks, TemplateMapper templates, AssetMapper assets, StorageService storage,
            @Value("${app.simulation.default-fail-stage}") String defaultFailStage,
            @Value("${app.simulation.default-step-delay-ms}") int defaultStepDelayMs,
            @Value("${app.simulation.allow-request-override}") boolean allowOverride) {
        this.tasks = tasks;
        this.templates = templates;
        this.assets = assets;
        this.storage = storage;
        this.defaultFailStage = defaultFailStage;
        this.defaultStepDelayMs = defaultStepDelayMs;
        this.allowOverride = allowOverride;
    }

    @Transactional
    public String submit(String userId, SubmitRequest request) {
        if (request == null || request.templateId() == null || request.assetIds() == null) {
            throw invalid("请指定模板和图片");
        }
        TemplateRow template = templates.findById(request.templateId());
        if (template == null) throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "模板不存在");
        if (request.assetIds().size() != template.requiredImageCount
                || new HashSet<>(request.assetIds()).size() != request.assetIds().size()) {
            throw invalid("图片数量与模板要求不符，或图片重复");
        }
        for (String assetId : request.assetIds()) {
            if (assetId == null) throw invalid("图片素材无效");
            AssetRow asset = assets.findOwned(assetId, userId);
            if (asset == null || !asset.templateId.equals(template.id)
                    || !Files.isRegularFile(storage.resolve(asset.storedPath))) {
                throw invalid("图片素材不可用，请重新上传");
            }
        }
        String failStage = defaultFailStage;
        int delay = defaultStepDelayMs;
        if (request.simulation() != null) {
            if (!allowOverride) throw invalid("当前环境不允许覆盖模拟设置");
            if (request.simulation().failStage() != null) failStage = request.simulation().failStage();
            if (request.simulation().stepDelayMs() != null) delay = request.simulation().stepDelayMs();
        }
        if (!List.of("NONE", "ASSET_CHECK", "GENERATE", "FINALIZE").contains(failStage)
                || delay < 0 || delay > 15000) throw invalid("模拟阶段或耗时设置无效");
        TaskRow task = new TaskRow();
        task.id = UUID.randomUUID().toString();
        task.userId = userId;
        task.templateId = template.id;
        task.failStage = failStage;
        task.stepDelayMs = delay;
        tasks.insert(task);
        for (int i = 0; i < request.assetIds().size(); i++) {
            tasks.insertAsset(task.id, request.assetIds().get(i), i);
        }
        return task.id;
    }

    public TaskView get(String userId, String taskId) {
        TaskRow row = owned(userId, taskId);
        return view(row);
    }

    public List<TaskView> history(String userId) {
        return tasks.history(userId, 100).stream().map(this::view).toList();
    }

    public TaskRow owned(String userId, String taskId) {
        TaskRow row = tasks.findOwned(taskId, userId);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在");
        return row;
    }

    private TaskView view(TaskRow row) {
        String resultUrl = "SUCCEEDED".equals(row.status) && row.resultPath != null
                ? "/api/tasks/" + row.id + "/result" : null;
        return new TaskView(row.id, row.templateId, row.templateTitle, row.status,
                row.currentStage, row.failureReason, resultUrl, row.createdAt,
                row.startedAt, row.finishedAt, tasks.events(row.id));
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TASK", message);
    }

    public record SimulationRequest(String failStage, Integer stepDelayMs) {}
    public record SubmitRequest(String templateId, List<String> assetIds, SimulationRequest simulation) {}
    public record TaskView(String id, String templateId, String templateTitle, String status,
                           String currentStage, String failureReason, String resultUrl,
                           LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt,
                           List<StageEventRow> stageHistory) {}
}
