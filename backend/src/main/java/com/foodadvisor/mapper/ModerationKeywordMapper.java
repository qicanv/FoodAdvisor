package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ModerationKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModerationKeywordMapper extends BaseMapper<ModerationKeyword> {

    @Select("SELECT * FROM moderation_keywords WHERE rule_code = #{ruleCode}")
    List<ModerationKeyword> findByRuleCode(String ruleCode);

    @Select("SELECT * FROM moderation_keywords WHERE rule_code IN (SELECT rule_code FROM moderation_rules WHERE enabled = true)")
    List<ModerationKeyword> findAllEnabled();
}