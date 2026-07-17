package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.RecommendationItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecommendationItemMapper
        extends BaseMapper<RecommendationItem> {
}