package com.example.materialstudio.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.materialstudio.mapper.AssetMapper;
import com.example.materialstudio.mapper.TaskMapper;
import com.example.materialstudio.model.AssetRow;
import com.example.materialstudio.model.TaskRow;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class TaskWorkerTest {
    @TempDir Path tempDir;
    TaskMapper tasks;
    AssetMapper assets;
    TaskTransitionService transitions;
    ImageComposer composer;
    StorageService storage;
    TaskWorker worker;
    TaskRow task;

    @BeforeEach
    void setup() throws Exception {
        tasks = mock(TaskMapper.class);
        assets = mock(AssetMapper.class);
        transitions = mock(TaskTransitionService.class);
        composer = mock(ImageComposer.class);
        storage = new StorageService(tempDir.toString());
        worker = new TaskWorker(tasks, assets, transitions, storage, composer);
        task = new TaskRow();
        task.id = "task-1";
        task.stepDelayMs = 0;
        task.failStage = "NONE";
        AssetRow asset = new AssetRow();
        asset.id = "asset-1";
        asset.storedPath = "uploads/sample.png";
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png",
                storage.resolve(asset.storedPath).toFile());
        when(tasks.nextQueuedId()).thenReturn(task.id);
        when(tasks.claim(task.id)).thenReturn(1);
        when(tasks.findById(task.id)).thenReturn(task);
        when(assets.findForTask(task.id)).thenReturn(List.of(asset));
        when(composer.compose(eq(task.id), anyList())).thenAnswer(call -> {
            Path temporary = storage.resolve("results/task-1.part.png");
            Files.write(temporary, new byte[] {1, 2, 3});
            return temporary;
        });
    }

    @Test
    void completesStagesInOrderAndPublishesResult() throws Exception {
        worker.processNext();

        InOrder order = inOrder(transitions, composer);
        order.verify(transitions).begin(task.id, "ASSET_CHECK");
        order.verify(transitions).complete(task.id, "ASSET_CHECK");
        order.verify(transitions).begin(task.id, "GENERATE");
        order.verify(composer).compose(eq(task.id), anyList());
        order.verify(transitions).complete(task.id, "GENERATE");
        order.verify(transitions).begin(task.id, "FINALIZE");
        order.verify(transitions).finish(task.id, "results/task-1.png");
        verify(transitions, never()).fail(anyString(), anyString(), anyString());
        assertTrue(Files.isRegularFile(storage.resolve("results/task-1.png")));
        assertFalse(Files.exists(storage.resolve("results/task-1.part.png")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSET_CHECK", "GENERATE", "FINALIZE"})
    void configuredFailureStopsLaterStages(String failedStage) {
        task.failStage = failedStage;

        worker.processNext();

        String label = switch (failedStage) {
            case "ASSET_CHECK" -> "素材检查";
            case "GENERATE" -> "模拟生成";
            default -> "结果整理";
        };
        verify(transitions).begin(task.id, failedStage);
        verify(transitions).fail(task.id, failedStage, "模拟失败：" + label);
        verify(transitions, never()).complete(task.id, failedStage);
        verify(transitions, never()).finish(eq(task.id), eq("results/task-1.png"));
        if ("ASSET_CHECK".equals(failedStage)) verify(transitions, never()).begin(task.id, "GENERATE");
        if (!"FINALIZE".equals(failedStage)) verify(transitions, never()).begin(task.id, "FINALIZE");
        assertFalse(Files.exists(storage.resolve("results/task-1.png")));
        assertFalse(Files.exists(storage.resolve("results/task-1.part.png")));
    }
}
