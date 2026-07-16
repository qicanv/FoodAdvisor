package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ChatSessionState;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionStateMapper
        extends BaseMapper<ChatSessionState> {
}