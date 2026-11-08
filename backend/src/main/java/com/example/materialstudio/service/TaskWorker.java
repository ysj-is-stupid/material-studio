package com.example.materialstudio.service;

import com.example.materialstudio.mapper.AssetMapper;
import com.example.materialstudio.mapper.TaskMapper;
import com.example.materialstudio.model.AssetRow;
import com.example.materialstudio.model.TaskRow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskWorker {
    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);
    private final TaskMapper tasks;
    private final AssetMapper assets;
    private final TaskTransitionService transitions;
    private final StorageService storage;
    private final ImageComposer composer;

    public TaskWorker(TaskMapper tasks, AssetMapper assets, TaskTransitionService transitions,
                      StorageService storage, ImageComposer composer) {
        this.tasks = tasks;
        this.assets = assets;
        this.transitions = transitions;
        this.storage = storage;
        this.composer = composer;
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-delay-ms:300}")
    public void processNext() {
        String id = tasks.nextQueuedId();
        if (id == null || tasks.claim(id) != 1) return;
        TaskRow task = tasks.findById(id);
        String stage = "ASSET_CHECK";
        Path temporary = null;
        try {
            List<AssetRow> taskAssets = assets.findForTask(id);
            transitions.begin(id, stage);
            delayAndMaybeFail(task, stage);
            checkAssets(taskAssets);
            transitions.complete(id, stage);

            stage = "GENERATE";
            transitions.begin(id, stage);
            delayAndMaybeFail(task, stage);
            temporary = composer.compose(id, taskAssets);
            transitions.complete(id, stage);

            stage = "FINALIZE";
            transitions.begin(id, stage);
            delayAndMaybeFail(task, stage);
            String resultPath = "results/" + id + ".png";
            Files.move(temporary, storage.resolve(resultPath), StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            transitions.finish(id, resultPath);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.info("Task {} interrupted at {}; it remains RUNNING", id, stage);
        } catch (Exception error) {
            String reason = error instanceof SimulatedFailure
                    ? "模拟失败：" + stageLabel(stage)
                    : "处理过程中发生错误，请查看服务端日志";
            if (error instanceof SimulatedFailure) {
                log.info("Task {} reached configured failure at {}", id, stage);
            } else {
                log.error("Task {} failed at {}", id, stage, error);
            }
            try {
                transitions.fail(id, stage, reason);
            } catch (Exception markError) {
                log.error("Could not persist failure for task {}", id, markError);
            }
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanupError) { log.warn("Could not remove temp result for {}", id, cleanupError); }
            }
        }
    }

    private void delayAndMaybeFail(TaskRow task, String stage) throws InterruptedException, SimulatedFailure {
        Thread.sleep(task.stepDelayMs);
        if (stage.equals(task.failStage)) throw new SimulatedFailure();
    }

    private void checkAssets(List<AssetRow> taskAssets) throws IOException {
        if (taskAssets.isEmpty()) throw new IOException("No task assets");
        for (AssetRow asset : taskAssets) {
            Path path = storage.resolve(asset.storedPath);
            if (!Files.isRegularFile(path) || ImageIO.read(path.toFile()) == null) {
                throw new IOException("Missing or invalid task asset");
            }
        }
    }

    private String stageLabel(String stage) {
        return switch (stage) {
            case "ASSET_CHECK" -> "素材检查";
            case "GENERATE" -> "模拟生成";
            case "FINALIZE" -> "结果整理";
            default -> "未知阶段";
        };
    }

    private static class SimulatedFailure extends Exception {}
}
