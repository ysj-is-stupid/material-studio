package com.example.materialstudio.mapper;

import org.apache.ibatis.annotations.Select;

public interface HealthMapper {
    @Select("SELECT 1")
    int checkDatabase();
}
