package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ModerationRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModerationRuleMapper extends BaseMapper<ModerationRule> {

    @Select("SELECT * FROM moderation_rules WHERE enabled = true ORDER BY risk_level DESC")
    List<ModerationRule> findAllEnabled();

    @Select("SELECT * FROM moderation_rules WHERE rule_code = #{ruleCode} AND enabled = true")
    ModerationRule findByCode(String ruleCode);
}