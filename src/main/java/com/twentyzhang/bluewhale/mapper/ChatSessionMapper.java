package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 原子认领：仅当会话尚未被接入时才写入 assignee。
     * 返回 1=接入成功，0=已被他人抢先（防两个客服并发认领同一会话）。
     */
    @Update("""
            UPDATE chat_session
            SET assignee_staff_id = #{staffId}, updated_at = NOW()
            WHERE id = #{id} AND assignee_staff_id IS NULL AND deleted = 0
            """)
    int claim(@Param("id") Long id, @Param("staffId") Long staffId);

    /**
     * 释放接待：仅当前接待人本人可释放，置回未接入。
     * 返回 1=释放成功，0=非本人接待或会话不存在。
     */
    @Update("""
            UPDATE chat_session
            SET assignee_staff_id = NULL, updated_at = NOW()
            WHERE id = #{id} AND assignee_staff_id = #{staffId} AND deleted = 0
            """)
    int release(@Param("id") Long id, @Param("staffId") Long staffId);
}
