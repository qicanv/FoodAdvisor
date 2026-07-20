package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.RateLimitEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RateLimitEventMapper extends BaseMapper<RateLimitEvent> {
}
