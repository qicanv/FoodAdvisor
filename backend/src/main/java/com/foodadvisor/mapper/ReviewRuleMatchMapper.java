package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ReviewRuleMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface ReviewRuleMatchMapper extends BaseMapper<ReviewRuleMatch> {

    @Select("SELECT rrm.*, mr.rule_name, mr.description, mr.risk_level FROM review_rule_matches rrm " +
            "LEFT JOIN moderation_rules mr ON rrm.rule_code = mr.rule_code " +
            "WHERE rrm.review_id = #{reviewId}")
    List<java.util.Map<String, Object>> findByReviewIdWithRuleInfo(Long reviewId);

    @Delete("DELETE FROM review_rule_matches WHERE review_id = #{reviewId}")
    void deleteByReviewId(Long reviewId);
}