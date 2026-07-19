package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.topic.ContentTagDTO;
import com.foodadvisor.dto.topic.TopicDTO;
import com.foodadvisor.dto.topic.TopicMerchantDTO;
import com.foodadvisor.dto.topic.TopicRequest;
import com.foodadvisor.entity.ContentTag;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantTagRelation;
import com.foodadvisor.entity.Topic;
import com.foodadvisor.entity.TopicMerchant;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.ContentTagMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.MerchantTagRelationMapper;
import com.foodadvisor.mapper.TopicMapper;
import com.foodadvisor.mapper.TopicMerchantMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TopicService {

    private final TopicMapper topicMapper;
    private final TopicMerchantMapper topicMerchantMapper;
    private final ContentTagMapper contentTagMapper;
    private final MerchantMapper merchantMapper;
    private final MerchantTagRelationMapper merchantTagRelationMapper;

    public TopicService(
            TopicMapper topicMapper,
            TopicMerchantMapper topicMerchantMapper,
            ContentTagMapper contentTagMapper,
            MerchantMapper merchantMapper,
            MerchantTagRelationMapper merchantTagRelationMapper
    ) {
        this.topicMapper = topicMapper;
        this.topicMerchantMapper = topicMerchantMapper;
        this.contentTagMapper = contentTagMapper;
        this.merchantMapper = merchantMapper;
        this.merchantTagRelationMapper = merchantTagRelationMapper;
    }

    public List<TopicDTO> listTopics(String status, String keyword) {
        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<Topic>()
                .orderByDesc(Topic::getUpdatedAt);

        if (status != null && !status.isBlank()) {
            wrapper.eq(Topic::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Topic::getName, keyword);
        }

        List<Topic> topics = topicMapper.selectList(wrapper);

        Map<Long, Long> merchantCountMap = topicMerchantMapper.selectList(null)
                .stream()
                .collect(Collectors.groupingBy(TopicMerchant::getTopicId, Collectors.counting()));

        return topics.stream()
                .map(topic -> TopicDTO.builder()
                        .id(topic.getId())
                        .name(topic.getName())
                        .description(topic.getDescription())
                        .coverUrl(topic.getCoverUrl())
                        .status(topic.getStatus())
                        .startAt(topic.getStartAt())
                        .endAt(topic.getEndAt())
                        .createdBy(topic.getCreatedBy())
                        .createdAt(topic.getCreatedAt())
                        .updatedAt(topic.getUpdatedAt())
                        .merchantCount(merchantCountMap.getOrDefault(topic.getId(), 0L).intValue())
                        .build())
                .collect(Collectors.toList());
    }

    public TopicDTO getTopic(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "专题不存在");
        }

        Long merchantCount = topicMerchantMapper.selectCount(
                new LambdaQueryWrapper<TopicMerchant>().eq(TopicMerchant::getTopicId, id)
        );

        return TopicDTO.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .coverUrl(topic.getCoverUrl())
                .status(topic.getStatus())
                .startAt(topic.getStartAt())
                .endAt(topic.getEndAt())
                .createdBy(topic.getCreatedBy())
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .merchantCount(merchantCount.intValue())
                .build();
    }

    @Transactional
    public TopicDTO createTopic(TopicRequest request) {
        validateTopicRequest(request);

        OffsetDateTime now = OffsetDateTime.now();
        Topic topic = new Topic();
        topic.setName(request.getName());
        topic.setDescription(request.getDescription());
        topic.setCoverUrl(request.getCoverUrl());
        topic.setStatus(request.getStatus());
        topic.setStartAt(request.getStartAt());
        topic.setEndAt(request.getEndAt());
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);

        topicMapper.insert(topic);

        if (request.getMerchantIds() != null && !request.getMerchantIds().isEmpty()) {
            saveTopicMerchants(topic.getId(), request.getMerchantIds());
        }

        return getTopic(topic.getId());
    }

    @Transactional
    public TopicDTO updateTopic(Long id, TopicRequest request) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "专题不存在");
        }

        validateTopicRequest(request);

        OffsetDateTime now = OffsetDateTime.now();
        topic.setName(request.getName());
        topic.setDescription(request.getDescription());
        topic.setCoverUrl(request.getCoverUrl());
        topic.setStatus(request.getStatus());
        topic.setStartAt(request.getStartAt());
        topic.setEndAt(request.getEndAt());
        topic.setUpdatedAt(now);

        topicMapper.updateById(topic);

        if (request.getMerchantIds() != null) {
            topicMerchantMapper.delete(
                    new LambdaQueryWrapper<TopicMerchant>().eq(TopicMerchant::getTopicId, id)
            );
            saveTopicMerchants(id, request.getMerchantIds());
        }

        return getTopic(id);
    }

    @Transactional
    public void deleteTopic(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "专题不存在");
        }

        topicMerchantMapper.delete(
                new LambdaQueryWrapper<TopicMerchant>().eq(TopicMerchant::getTopicId, id)
        );
        topicMapper.deleteById(id);
    }

    public List<TopicMerchantDTO> getTopicMerchants(Long topicId) {
        Topic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "专题不存在");
        }

        List<TopicMerchant> topicMerchants = topicMerchantMapper.selectList(
                new LambdaQueryWrapper<TopicMerchant>()
                        .eq(TopicMerchant::getTopicId, topicId)
                        .orderByAsc(TopicMerchant::getSortOrder)
        );

        if (topicMerchants.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> merchantIds = topicMerchants.stream()
                .map(TopicMerchant::getMerchantId)
                .collect(Collectors.toList());

        List<Merchant> merchants = merchantMapper.selectBatchIds(merchantIds);
        Map<Long, Merchant> merchantMap = merchants.stream()
                .collect(Collectors.toMap(Merchant::getId, m -> m));

        return topicMerchants.stream()
                .map(tm -> {
                    Merchant m = merchantMap.get(tm.getMerchantId());
                    if (m == null) return null;
                    return TopicMerchantDTO.builder()
                            .id(m.getId())
                            .merchantCode(m.getMerchantCode())
                            .name(m.getName())
                            .category(m.getCategory())
                            .cuisine(m.getCuisine())
                            .rating(m.getRating())
                            .averagePrice(m.getAveragePrice())
                            .operationStatus(m.getOperationStatus())
                            .description(m.getDescription())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addTopicMerchant(Long topicId, Long merchantId) {
        Topic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "专题不存在");
        }

        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "商家不存在");
        }

        if (!"OPERATING".equals(merchant.getOperationStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MERCHANT_NOT_OPERATING", "已停业商家不能加入公开专题");
        }

        if (!"ACTIVE".equals(merchant.getPlatformStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MERCHANT_NOT_ACTIVE", "禁用或归档商家不能加入公开专题");
        }

        TopicMerchant existing = topicMerchantMapper.selectOne(
                new LambdaQueryWrapper<TopicMerchant>()
                        .eq(TopicMerchant::getTopicId, topicId)
                        .eq(TopicMerchant::getMerchantId, merchantId)
        );

        if (existing != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MERCHANT_ALREADY_ADDED", "商家已在专题中");
        }

        TopicMerchant tm = new TopicMerchant();
        tm.setTopicId(topicId);
        tm.setMerchantId(merchantId);
        tm.setSortOrder(0);
        tm.setCreatedAt(OffsetDateTime.now());
        topicMerchantMapper.insert(tm);
    }

    @Transactional
    public void removeTopicMerchant(Long topicId, Long merchantId) {
        topicMerchantMapper.delete(
                new LambdaQueryWrapper<TopicMerchant>()
                        .eq(TopicMerchant::getTopicId, topicId)
                        .eq(TopicMerchant::getMerchantId, merchantId)
        );
    }

    public List<ContentTagDTO> listTags(String category, String keyword) {
        LambdaQueryWrapper<ContentTag> wrapper = new LambdaQueryWrapper<ContentTag>()
                .eq(ContentTag::getStatus, "ACTIVE")
                .orderByAsc(ContentTag::getCategory)
                .orderByAsc(ContentTag::getName);

        if (category != null && !category.isBlank()) {
            wrapper.eq(ContentTag::getCategory, category);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ContentTag::getName, keyword);
        }

        List<ContentTag> tags = contentTagMapper.selectList(wrapper);

        Map<Long, Long> tagCountMap = merchantTagRelationMapper.selectList(null)
                .stream()
                .collect(Collectors.groupingBy(MerchantTagRelation::getTagId, Collectors.counting()));

        Set<String> seenNames = new HashSet<>();
        return tags.stream()
                .filter(tag -> seenNames.add(tag.getName()))
                .map(tag -> ContentTagDTO.builder()
                        .id(tag.getId())
                        .code(tag.getCode())
                        .name(tag.getName())
                        .category(tag.getCategory())
                        .status(tag.getStatus())
                        .createdAt(tag.getCreatedAt())
                        .updatedAt(tag.getUpdatedAt())
                        .count(tagCountMap.getOrDefault(tag.getId(), 0L).intValue())
                        .build())
                .collect(Collectors.toList());
    }

    public ContentTagDTO getTag(Long id) {
        ContentTag tag = contentTagMapper.selectById(id);
        if (tag == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "标签不存在");
        }
        return ContentTagDTO.builder()
                .id(tag.getId())
                .code(tag.getCode())
                .name(tag.getName())
                .category(tag.getCategory())
                .status(tag.getStatus())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .count(0)
                .build();
    }

    @Transactional
    public ContentTagDTO createTag(String name, String category) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TAG_NAME_REQUIRED", "标签名称不能为空");
        }
        if (category == null || category.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TAG_CATEGORY_REQUIRED", "标签类型不能为空");
        }

        ContentTag existing = contentTagMapper.selectOne(
                new LambdaQueryWrapper<ContentTag>()
                        .eq(ContentTag::getName, name)
                        .eq(ContentTag::getCategory, category)
        );

        if (existing != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TAG_EXISTS", "该标签已存在");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ContentTag tag = new ContentTag();
        tag.setCode(UUID.randomUUID().toString().substring(0, 8));
        tag.setName(name);
        tag.setCategory(category);
        tag.setStatus("ACTIVE");
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);

        contentTagMapper.insert(tag);
        return getTag(tag.getId());
    }

    @Transactional
    public void deleteTag(Long id) {
        ContentTag tag = contentTagMapper.selectById(id);
        if (tag == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "标签不存在");
        }
        merchantTagRelationMapper.delete(
                new LambdaQueryWrapper<MerchantTagRelation>().eq(MerchantTagRelation::getTagId, id)
        );
        contentTagMapper.deleteById(id);
    }

    @Transactional
    public void cleanDuplicateTags() {
        List<ContentTag> allTags = contentTagMapper.selectList(null);
        Map<String, List<ContentTag>> nameGroupMap = allTags.stream()
                .collect(Collectors.groupingBy(ContentTag::getName));

        for (Map.Entry<String, List<ContentTag>> entry : nameGroupMap.entrySet()) {
            List<ContentTag> tagsWithSameName = entry.getValue();
            if (tagsWithSameName.size() > 1) {
                ContentTag primaryTag = tagsWithSameName.get(0);
                for (int i = 1; i < tagsWithSameName.size(); i++) {
                    ContentTag duplicateTag = tagsWithSameName.get(i);
                    List<MerchantTagRelation> relations = merchantTagRelationMapper.selectList(
                            new LambdaQueryWrapper<MerchantTagRelation>().eq(MerchantTagRelation::getTagId, duplicateTag.getId())
                    );
                    for (MerchantTagRelation relation : relations) {
                        MerchantTagRelation existing = merchantTagRelationMapper.selectOne(
                                new LambdaQueryWrapper<MerchantTagRelation>()
                                        .eq(MerchantTagRelation::getMerchantId, relation.getMerchantId())
                                        .eq(MerchantTagRelation::getTagId, primaryTag.getId())
                        );
                        if (existing == null) {
                            relation.setTagId(primaryTag.getId());
                            merchantTagRelationMapper.updateById(relation);
                        }
                    }
                    contentTagMapper.deleteById(duplicateTag.getId());
                }
            }
        }
    }

    public List<TopicMerchantDTO> getTagMerchants(Long tagId) {
        ContentTag tag = contentTagMapper.selectById(tagId);
        if (tag == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "标签不存在");
        }

        List<MerchantTagRelation> relations = merchantTagRelationMapper.selectList(
                new LambdaQueryWrapper<MerchantTagRelation>().eq(MerchantTagRelation::getTagId, tagId)
        );

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> merchantIds = relations.stream()
                .map(MerchantTagRelation::getMerchantId)
                .collect(Collectors.toList());

        List<Merchant> merchants = merchantMapper.selectBatchIds(merchantIds);
        Map<Long, Merchant> merchantMap = merchants.stream()
                .collect(Collectors.toMap(Merchant::getId, m -> m));

        return relations.stream()
                .map(relation -> {
                    Merchant m = merchantMap.get(relation.getMerchantId());
                    if (m == null) return null;
                    return TopicMerchantDTO.builder()
                            .id(m.getId())
                            .merchantCode(m.getMerchantCode())
                            .name(m.getName())
                            .category(m.getCategory())
                            .cuisine(m.getCuisine())
                            .rating(m.getRating())
                            .averagePrice(m.getAveragePrice())
                            .operationStatus(m.getOperationStatus())
                            .description(m.getDescription())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void validateTopicRequest(TopicRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOPIC_NAME_REQUIRED", "专题名称不能为空");
        }
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOPIC_STATUS_REQUIRED", "专题状态不能为空");
        }
        if (!Set.of("PUBLISHED", "DRAFT", "OFFLINE").contains(request.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "状态必须是 PUBLISHED、DRAFT 或 OFFLINE");
        }
    }

    private void saveTopicMerchants(Long topicId, List<Long> merchantIds) {
        OffsetDateTime now = OffsetDateTime.now();
        int sortOrder = 0;
        for (Long merchantId : merchantIds) {
            TopicMerchant existing = topicMerchantMapper.selectOne(
                    new LambdaQueryWrapper<TopicMerchant>()
                            .eq(TopicMerchant::getTopicId, topicId)
                            .eq(TopicMerchant::getMerchantId, merchantId)
            );
            if (existing != null) continue;

            TopicMerchant tm = new TopicMerchant();
            tm.setTopicId(topicId);
            tm.setMerchantId(merchantId);
            tm.setSortOrder(sortOrder++);
            tm.setCreatedAt(now);
            topicMerchantMapper.insert(tm);
        }
    }
}