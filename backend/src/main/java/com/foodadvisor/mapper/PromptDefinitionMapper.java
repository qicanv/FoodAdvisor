package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.PromptDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PromptDefinitionMapper
        extends BaseMapper<PromptDefinition> {
}