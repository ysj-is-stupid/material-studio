package com.example.materialstudio.model;

import java.time.LocalDateTime;

public class TaskRow {
    public String id;
    public String userId;
    public String templateId;
    public String templateTitle;
    public String status;
    public String currentStage;
    public String resultPath;
    public String failureReason;
    public String failStage;
    public int stepDelayMs;
    public LocalDateTime createdAt;
    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;
}
