package com.example.materialstudio.mapper;

import com.example.materialstudio.model.StageEventRow;
import com.example.materialstudio.model.TaskRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TaskMapper {
    @Insert("INSERT INTO production_task (id, user_id, template_id, status, current_stage, fail_stage, step_delay_ms) "
            + "VALUES (#{id}, #{userId}, #{templateId}, 'QUEUED', 'NONE', #{failStage}, #{stepDelayMs})")
    void insert(TaskRow task);

    @Insert("INSERT INTO task_asset (task_id, asset_id, position) VALUES (#{taskId}, #{assetId}, #{position})")
    void insertAsset(@Param("taskId") String taskId, @Param("assetId") String assetId,
                     @Param("position") int position);

    @Select("SELECT t.*, p.title AS template_title FROM production_task t "
            + "JOIN template p ON p.id = t.template_id WHERE t.id = #{id}")
    TaskRow findById(@Param("id") String id);

    @Select("SELECT t.*, p.title AS template_title FROM production_task t "
            + "JOIN template p ON p.id = t.template_id "
            + "WHERE t.id = #{id} AND t.user_id = #{userId}")
    TaskRow findOwned(@Param("id") String id, @Param("userId") String userId);

    @Select("SELECT t.*, p.title AS template_title FROM production_task t "
            + "JOIN template p ON p.id = t.template_id WHERE t.user_id = #{userId} "
            + "ORDER BY t.created_at DESC, t.id DESC LIMIT #{limit}")
    List<TaskRow> history(@Param("userId") String userId, @Param("limit") int limit);

    @Select("SELECT stage, state, started_at, finished_at, failure_reason "
            + "FROM task_stage_event WHERE task_id = #{taskId} "
            + "ORDER BY started_at, FIELD(stage, 'ASSET_CHECK', 'GENERATE', 'FINALIZE')")
    List<StageEventRow> events(@Param("taskId") String taskId);

    @Select("SELECT id FROM production_task WHERE status = 'QUEUED' ORDER BY created_at, id LIMIT 1")
    String nextQueuedId();

    @Update("UPDATE production_task SET status = 'RUNNING', started_at = UTC_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'QUEUED'")
    int claim(@Param("id") String id);

    @Update("UPDATE production_task SET current_stage = #{stage} "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int setStage(@Param("id") String id, @Param("stage") String stage);

    @Insert("INSERT INTO task_stage_event (task_id, stage, state, started_at) "
            + "VALUES (#{taskId}, #{stage}, 'RUNNING', UTC_TIMESTAMP(6))")
    void startEvent(@Param("taskId") String taskId, @Param("stage") String stage);

    @Update("UPDATE task_stage_event SET state = 'SUCCEEDED', finished_at = UTC_TIMESTAMP(6) "
            + "WHERE task_id = #{taskId} AND stage = #{stage} AND state = 'RUNNING'")
    int completeEvent(@Param("taskId") String taskId, @Param("stage") String stage);

    @Update("UPDATE task_stage_event SET state = 'FAILED', failure_reason = #{reason}, "
            + "finished_at = UTC_TIMESTAMP(6) WHERE task_id = #{taskId} AND stage = #{stage} AND state = 'RUNNING'")
    int failEvent(@Param("taskId") String taskId, @Param("stage") String stage,
                  @Param("reason") String reason);

    @Update("UPDATE production_task SET status = 'SUCCEEDED', result_path = #{path}, "
            + "finished_at = UTC_TIMESTAMP(6) WHERE id = #{id} AND status = 'RUNNING' AND current_stage = 'FINALIZE'")
    int finish(@Param("id") String id, @Param("path") String path);

    @Update("UPDATE production_task SET status = 'FAILED', current_stage = #{stage}, "
            + "failure_reason = #{reason}, finished_at = UTC_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int fail(@Param("id") String id, @Param("stage") String stage, @Param("reason") String reason);
}
