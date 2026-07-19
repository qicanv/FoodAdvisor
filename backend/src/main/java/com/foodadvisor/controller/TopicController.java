package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.topic.ContentTagDTO;
import com.foodadvisor.dto.topic.TopicDTO;
import com.foodadvisor.dto.topic.TopicMerchantDTO;
import com.foodadvisor.dto.topic.TopicRequest;
import com.foodadvisor.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TopicDTO>>> listTopics(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<TopicDTO> topics = topicService.listTopics(status, keyword);
        return ResponseEntity.ok(ApiResponse.success(topics));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TopicDTO>> getTopic(@PathVariable Long id) {
        TopicDTO topic = topicService.getTopic(id);
        return ResponseEntity.ok(ApiResponse.success(topic));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TopicDTO>> createTopic(@RequestBody TopicRequest request) {
        TopicDTO topic = topicService.createTopic(request);
        return ResponseEntity.ok(ApiResponse.success(topic));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TopicDTO>> updateTopic(
            @PathVariable Long id,
            @RequestBody TopicRequest request) {
        TopicDTO topic = topicService.updateTopic(id, request);
        return ResponseEntity.ok(ApiResponse.success(topic));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(@PathVariable Long id) {
        topicService.deleteTopic(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/merchants")
    public ResponseEntity<ApiResponse<List<TopicMerchantDTO>>> getTopicMerchants(@PathVariable Long id) {
        List<TopicMerchantDTO> merchants = topicService.getTopicMerchants(id);
        return ResponseEntity.ok(ApiResponse.success(merchants));
    }

    @PostMapping("/{id}/merchants")
    public ResponseEntity<ApiResponse<Void>> addTopicMerchant(
            @PathVariable Long id,
            @RequestBody Long merchantId) {
        topicService.addTopicMerchant(id, merchantId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/merchants/{merchantId}")
    public ResponseEntity<ApiResponse<Void>> removeTopicMerchant(
            @PathVariable Long id,
            @PathVariable Long merchantId) {
        topicService.removeTopicMerchant(id, merchantId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<ContentTagDTO>>> listTags(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        List<ContentTagDTO> tags = topicService.listTags(category, keyword);
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    @GetMapping("/tags/{id}")
    public ResponseEntity<ApiResponse<ContentTagDTO>> getTag(@PathVariable Long id) {
        ContentTagDTO tag = topicService.getTag(id);
        return ResponseEntity.ok(ApiResponse.success(tag));
    }

    @PostMapping("/tags")
    public ResponseEntity<ApiResponse<ContentTagDTO>> createTag(
            @RequestParam String name,
            @RequestParam String category) {
        ContentTagDTO tag = topicService.createTag(name, category);
        return ResponseEntity.ok(ApiResponse.success(tag));
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
        topicService.deleteTag(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/tags/{id}/merchants")
    public ResponseEntity<ApiResponse<List<TopicMerchantDTO>>> getTagMerchants(@PathVariable Long id) {
        List<TopicMerchantDTO> merchants = topicService.getTagMerchants(id);
        return ResponseEntity.ok(ApiResponse.success(merchants));
    }

    @PostMapping("/tags/clean-duplicates")
    public ResponseEntity<ApiResponse<Void>> cleanDuplicateTags() {
        topicService.cleanDuplicateTags();
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}