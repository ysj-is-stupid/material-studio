package com.example.materialstudio.mapper;

import com.example.materialstudio.model.AssetRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AssetMapper {
    @Insert("INSERT INTO asset (id, user_id, template_id, stored_path, mime_type, size_bytes) "
            + "VALUES (#{id}, #{userId}, #{templateId}, #{storedPath}, #{mimeType}, #{sizeBytes})")
    void insert(AssetRow asset);

    @Select("SELECT * FROM asset WHERE id = #{id} AND user_id = #{userId}")
    AssetRow findOwned(@Param("id") String id, @Param("userId") String userId);

    @Select("SELECT a.* FROM asset a JOIN task_asset ta ON ta.asset_id = a.id "
            + "WHERE ta.task_id = #{taskId} ORDER BY ta.position")
    List<AssetRow> findForTask(@Param("taskId") String taskId);
}
