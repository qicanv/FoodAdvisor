package com.foodadvisor.service;

import com.foodadvisor.entity.OpenSearchSyncTask;
import com.foodadvisor.entity.Review;
import com.foodadvisor.mapper.OpenSearchSyncTaskMapper;
import com.foodadvisor.mapper.ReviewMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenSearchSyncServiceTest {

    @Test
    void mapsDishOutboxTypeToMenuAndDeduplicatesPendingTask() {
        OpenSearchSyncTaskMapper mapper = mock(OpenSearchSyncTaskMapper.class);
        OpenSearchSyncService service = new OpenSearchSyncService(
                mapper, mock(AIClientService.class),
                mock(KnowledgeEnrichmentService.class));
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(OpenSearchSyncTask.class))).thenAnswer(invocation -> {
            OpenSearchSyncTask task = invocation.getArgument(0);
            task.setId(1L);
            return 1;
        });

        OpenSearchSyncTask task = service.createSyncTask(
                "DISH", 9L, OpenSearchSyncTask.OP_UPSERT);

        assertEquals("MENU", task.getSourceType());
        verify(mapper).insert(task);
    }

    @Test
    void hiddenReviewTaskDeactivatesReviewDocument() {
        OpenSearchSyncTaskMapper mapper = mock(OpenSearchSyncTaskMapper.class);
        AIClientService client = mock(AIClientService.class);
        OpenSearchSyncService service = new OpenSearchSyncService(
                mapper, client, mock(KnowledgeEnrichmentService.class));
        ReviewMapper reviewMapper = mock(ReviewMapper.class);
        ReflectionTestUtils.setField(service, "reviewMapper", reviewMapper);
        OpenSearchSyncTask task = new OpenSearchSyncTask();
        task.setId(3L);
        task.setSourceType("REVIEW");
        task.setSourceId(88L);
        task.setOperationType(OpenSearchSyncTask.OP_UPSERT);
        task.setStatus(OpenSearchSyncTask.STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryAt(OffsetDateTime.now().minusSeconds(1));
        Review hidden = new Review();
        hidden.setId(88L);
        hidden.setStatus("HIDDEN");
        hidden.setModerationStatus("APPROVED");
        when(reviewMapper.selectById(88L)).thenReturn(hidden);
        when(mapper.selectList(any())).thenReturn(List.of(task));
        when(mapper.selectById(3L)).thenReturn(task);

        assertEquals(1, service.processPendingTasks());
        verify(client).deactivateKnowledge("REVIEW", List.of(88L));
        assertEquals(OpenSearchSyncTask.STATUS_SUCCESS, task.getStatus());
    }
}
