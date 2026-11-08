package com.example.materialstudio.mapper;

import com.example.materialstudio.model.TemplateRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TemplateMapper {
    @Select("SELECT * FROM template WHERE enabled = TRUE ORDER BY category, id")
    List<TemplateRow> findAll();

    @Select("SELECT * FROM template WHERE id = #{id} AND enabled = TRUE")
    TemplateRow findById(@Param("id") String id);
}
