package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMapper
        extends BaseMapper<ChatMessage> {

    @Select("SELECT nextval(pg_get_serial_sequence('chat_messages', 'id'))")
    Long nextId();

    @Insert("""
            INSERT INTO chat_messages (
                id, session_id, role, content, message_type,
                request_id, metadata, created_at
            )
            VALUES (
                #{id}, #{sessionId}, #{role}, #{content},
                #{messageType}, #{requestId}, #{metadata}::jsonb,
                #{createdAt}
            )
            """)
    int insertReserved(ChatMessage message);
}
