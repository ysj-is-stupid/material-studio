package com.example.materialstudio.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface IdentityMapper {
    @Select("SELECT id FROM demo_user WHERE token_hash = #{hash}")
    String findIdByHash(@Param("hash") String hash);

    @Insert("INSERT INTO demo_user (id, token_hash) VALUES (#{id}, #{hash})")
    void insert(@Param("id") String id, @Param("hash") String hash);
}
