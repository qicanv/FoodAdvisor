package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.entity.ReviewTagRelation;
import com.foodadvisor.mapper.ReviewAnalysisMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.ReviewTagRelationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评价服务
 */
@Service
public class ReviewService extends ServiceImpl<ReviewMapper, Review> {

    private final ReviewAnalysisMapper analysisMapper;
    private final ReviewTagRelationMapper tagRelationMapper;

    public ReviewService(ReviewAnalysisMapper analysisMapper,
                         ReviewTagRelationMapper tagRelationMapper) {
        this.analysisMapper = analysisMapper;
        this.tagRelationMapper = tagRelationMapper;
    }

    /**
     * 按商家分页查询公开评价
     */
    public Page<Review> listByMerchant(Long merchantId, int pageNum, int pageSize) {
        Page<Review> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getMerchantId, merchantId)
               .eq(Review::getStatus, "PUBLISHED")
               .orderByDesc(Review::getReviewTime);
        return this.page(page, wrapper);
    }

    /**
     * 获取评价的分析结果
     */
    public ReviewAnalysis getAnalysis(Long reviewId) {
        LambdaQueryWrapper<ReviewAnalysis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewAnalysis::getReviewId, reviewId);
        return analysisMapper.selectOne(wrapper);
    }

    /**
     * 获取评价的标签关联列表
     */
    public List<ReviewTagRelation> getTagRelations(Long reviewId) {
        LambdaQueryWrapper<ReviewTagRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewTagRelation::getReviewId, reviewId);
        return tagRelationMapper.selectList(wrapper);
    }

    /**
     * 保存或更新分析结果
     */
    @Transactional
    public void saveAnalysis(ReviewAnalysis analysis) {
        LambdaQueryWrapper<ReviewAnalysis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewAnalysis::getReviewId, analysis.getReviewId());
        ReviewAnalysis existing = analysisMapper.selectOne(wrapper);
        if (existing != null) {
            analysis.setId(existing.getId());
            analysisMapper.updateById(analysis);
        } else {
            analysisMapper.insert(analysis);
        }
    }

    /**
     * 批量保存标签关联
     */
    @Transactional
    public void saveTagRelations(List<ReviewTagRelation> relations) {
        for (ReviewTagRelation rel : relations) {
            // 删除旧关联后插入
            LambdaQueryWrapper<ReviewTagRelation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReviewTagRelation::getReviewId, rel.getReviewId())
                   .eq(ReviewTagRelation::getTagId, rel.getTagId());
            tagRelationMapper.delete(wrapper);
            tagRelationMapper.insert(rel);
        }
    }

    /**
     * 统计某商家已分析评价的情感分布
     */
    public long countBySentiment(Long merchantId, String sentiment) {
        LambdaQueryWrapper<Review> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.eq(Review::getMerchantId, merchantId)
                     .eq(Review::getStatus, "PUBLISHED");
        List<Long> reviewIds = this.list(reviewWrapper)
                .stream().map(Review::getId).toList();

        if (reviewIds.isEmpty()) return 0;

        LambdaQueryWrapper<ReviewAnalysis> analysisWrapper = new LambdaQueryWrapper<>();
        analysisWrapper.in(ReviewAnalysis::getReviewId, reviewIds)
                        .eq(ReviewAnalysis::getSentiment, sentiment);
        return analysisMapper.selectCount(analysisWrapper);
    }
}
