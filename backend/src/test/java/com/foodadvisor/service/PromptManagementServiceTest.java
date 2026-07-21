package com.foodadvisor.service;

import com.foodadvisor.dto.prompt.CreatePromptVersionRequest;
import com.foodadvisor.dto.prompt.PromptVersionResponse;
import com.foodadvisor.dto.prompt.PromptVersionSwitchRequest;
import com.foodadvisor.dto.prompt.ResolvedPrompt;
import com.foodadvisor.entity.PromptActivationLog;
import com.foodadvisor.entity.PromptDefinition;
import com.foodadvisor.entity.PromptVersion;
import com.foodadvisor.enums.PromptScene;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.PromptActivationLogMapper;
import com.foodadvisor.mapper.PromptDefinitionMapper;
import com.foodadvisor.mapper.PromptVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptManagementServiceTest {

    @Mock
    private PromptDefinitionMapper promptDefinitionMapper;

    @Mock
    private PromptVersionMapper promptVersionMapper;

    @Mock
    private PromptActivationLogMapper promptActivationLogMapper;

    private PromptManagementService promptManagementService;

    @BeforeEach
    void setUp() {
        promptManagementService = new PromptManagementService(
                promptDefinitionMapper,
                promptVersionMapper,
                promptActivationLogMapper
        );
    }

    @Test
    void shouldCreateFirstVersionAndActivateIt() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", null);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        when(promptVersionMapper.selectOne(any()))
                .thenReturn(null);

        doAnswer(invocation -> {
            PromptVersion inserted =
                    invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        }).when(promptVersionMapper)
                .insert(any(PromptVersion.class));

        CreatePromptVersionRequest request =
                new CreatePromptVersionRequest(
                        "You are a review summary assistant.",
                        "Initial review summary prompt",
                        true
                );

        PromptVersionResponse response =
                promptManagementService.createVersion(
                        "review_summary",
                        request,
                        7L
                );

        assertAll(
                () -> assertEquals(101L, response.id()),
                () -> assertEquals(
                        "REVIEW_SUMMARY",
                        response.sceneCode()
                ),
                () -> assertEquals(1, response.versionNo()),
                () -> assertEquals(
                        "review-summary:v1",
                        response.versionTag()
                ),
                () -> assertTrue(response.active()),
                () -> assertEquals(
                        7L,
                        response.createdBy()
                )
        );

        ArgumentCaptor<PromptVersion> versionCaptor =
                ArgumentCaptor.forClass(PromptVersion.class);

        verify(promptVersionMapper)
                .insert(versionCaptor.capture());

        PromptVersion insertedVersion =
                versionCaptor.getValue();

        assertAll(
                () -> assertEquals(
                        3L,
                        insertedVersion.getPromptDefinitionId()
                ),
                () -> assertEquals(
                        1,
                        insertedVersion.getVersionNo()
                ),
                () -> assertEquals(
                        "review-summary:v1",
                        insertedVersion.getVersionTag()
                ),
                () -> assertEquals(
                        "You are a review summary assistant.",
                        insertedVersion.getContent()
                ),
                () -> assertEquals(
                        "Initial review summary prompt",
                        insertedVersion.getChangeNote()
                ),
                () -> assertEquals(
                        7L,
                        insertedVersion.getCreatedBy()
                )
        );

        ArgumentCaptor<PromptDefinition> definitionCaptor =
                ArgumentCaptor.forClass(PromptDefinition.class);

        verify(promptDefinitionMapper)
                .updateById(definitionCaptor.capture());

        assertEquals(
                101L,
                definitionCaptor.getValue().getActiveVersionId()
        );

        ArgumentCaptor<PromptActivationLog> logCaptor =
                ArgumentCaptor.forClass(PromptActivationLog.class);

        verify(promptActivationLogMapper)
                .insert(logCaptor.capture());

        PromptActivationLog activationLog =
                logCaptor.getValue();

        assertAll(
                () -> assertEquals(
                        3L,
                        activationLog.getPromptDefinitionId()
                ),
                () -> assertNull(
                        activationLog.getFromVersionId()
                ),
                () -> assertEquals(
                        101L,
                        activationLog.getToVersionId()
                ),
                () -> assertEquals(
                        "ACTIVATE",
                        activationLog.getOperationType()
                ),
                () -> assertEquals(
                        7L,
                        activationLog.getOperatedBy()
                )
        );
    }

    @Test
    void shouldCreateNextVersionWithoutActivatingIt() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", 100L);

        PromptVersion latestVersion =
                version(100L, 3L, 1);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        when(promptVersionMapper.selectOne(any()))
                .thenReturn(latestVersion);

        doAnswer(invocation -> {
            PromptVersion inserted =
                    invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        }).when(promptVersionMapper)
                .insert(any(PromptVersion.class));

        PromptVersionResponse response =
                promptManagementService.createVersion(
                        "REVIEW_SUMMARY",
                        new CreatePromptVersionRequest(
                                "Updated review summary prompt.",
                                "Improve output structure",
                                false
                        ),
                        8L
                );

        assertAll(
                () -> assertEquals(2, response.versionNo()),
                () -> assertEquals(
                        "review-summary:v2",
                        response.versionTag()
                ),
                () -> assertFalse(response.active()),
                () -> assertEquals(8L, response.createdBy())
        );

        verify(
                promptDefinitionMapper,
                never()
        ).updateById(any(PromptDefinition.class));

        verify(
                promptActivationLogMapper,
                never()
        ).insert(any(PromptActivationLog.class));
    }

    @Test
    void shouldRejectVersionBelongingToAnotherScene() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", null);

        PromptVersion foreignVersion =
                version(77L, 4L, 1);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        when(promptVersionMapper.selectById(77L))
                .thenReturn(foreignVersion);

        assertThrows(
                ApiException.class,
                () -> promptManagementService.activateVersion(
                        "REVIEW_SUMMARY",
                        77L,
                        new PromptVersionSwitchRequest(
                                "Invalid cross-scene activation"
                        ),
                        1L
                )
        );

        verify(
                promptDefinitionMapper,
                never()
        ).updateById(any(PromptDefinition.class));

        verify(
                promptActivationLogMapper,
                never()
        ).insert(any(PromptActivationLog.class));
    }

    @Test
    void shouldRollbackToOlderVersionAndRecordHistory() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", 22L);

        PromptVersion currentVersion =
                version(22L, 3L, 2);

        PromptVersion targetVersion =
                version(11L, 3L, 1);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        when(promptVersionMapper.selectById(22L))
                .thenReturn(currentVersion);

        when(promptVersionMapper.selectById(11L))
                .thenReturn(targetVersion);

        PromptVersionResponse response =
                promptManagementService.rollbackVersion(
                        "REVIEW_SUMMARY",
                        11L,
                        new PromptVersionSwitchRequest(
                                "Version 2 produced unstable output"
                        ),
                        9L
                );

        assertAll(
                () -> assertEquals(11L, response.id()),
                () -> assertEquals(1, response.versionNo()),
                () -> assertTrue(response.active())
        );

        ArgumentCaptor<PromptDefinition> definitionCaptor =
                ArgumentCaptor.forClass(PromptDefinition.class);

        verify(promptDefinitionMapper)
                .updateById(definitionCaptor.capture());

        assertEquals(
                11L,
                definitionCaptor.getValue().getActiveVersionId()
        );

        ArgumentCaptor<PromptActivationLog> logCaptor =
                ArgumentCaptor.forClass(PromptActivationLog.class);

        verify(promptActivationLogMapper)
                .insert(logCaptor.capture());

        PromptActivationLog rollbackLog =
                logCaptor.getValue();

        assertAll(
                () -> assertEquals(
                        22L,
                        rollbackLog.getFromVersionId()
                ),
                () -> assertEquals(
                        11L,
                        rollbackLog.getToVersionId()
                ),
                () -> assertEquals(
                        "ROLLBACK",
                        rollbackLog.getOperationType()
                ),
                () -> assertEquals(
                        "Version 2 produced unstable output",
                        rollbackLog.getOperationNote()
                ),
                () -> assertEquals(
                        9L,
                        rollbackLog.getOperatedBy()
                )
        );
    }

    @Test
    void shouldRejectRollbackToNewerVersion() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", 22L);

        PromptVersion currentVersion =
                version(22L, 3L, 2);

        PromptVersion newerVersion =
                version(33L, 3L, 3);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        when(promptVersionMapper.selectById(22L))
                .thenReturn(currentVersion);

        when(promptVersionMapper.selectById(33L))
                .thenReturn(newerVersion);

        assertThrows(
                ApiException.class,
                () -> promptManagementService.rollbackVersion(
                        "REVIEW_SUMMARY",
                        33L,
                        new PromptVersionSwitchRequest(
                                "Invalid rollback target"
                        ),
                        1L
                )
        );

        verify(
                promptDefinitionMapper,
                never()
        ).updateById(any(PromptDefinition.class));

        verify(
                promptActivationLogMapper,
                never()
        ).insert(any(PromptActivationLog.class));
    }

    @Test
    void shouldRejectRollbackWhenNoVersionIsActive() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", null);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        assertThrows(
                ApiException.class,
                () -> promptManagementService.rollbackVersion(
                        "REVIEW_SUMMARY",
                        11L,
                        new PromptVersionSwitchRequest(
                                "No active version"
                        ),
                        1L
                )
        );

        verify(
                promptVersionMapper,
                never()
        ).selectById(any());

        verify(
                promptDefinitionMapper,
                never()
        ).updateById(any(PromptDefinition.class));
    }

    @Test
    void shouldResolveActivePromptForRuntimeRequest() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", 101L);

        PromptVersion activeVersion =
                version(101L, 3L, 2);

        activeVersion.setContent(
                "Runtime review summary system prompt."
        );

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        when(promptVersionMapper.selectById(101L))
                .thenReturn(activeVersion);

        ResolvedPrompt resolved =
                promptManagementService.resolveActivePrompt(
                        PromptScene.REVIEW_SUMMARY
                ).orElseThrow();

        assertAll(
                () -> assertEquals(
                        3L,
                        resolved.definitionId()
                ),
                () -> assertEquals(
                        101L,
                        resolved.versionId()
                ),
                () -> assertEquals(
                        "REVIEW_SUMMARY",
                        resolved.sceneCode()
                ),
                () -> assertEquals(
                        2,
                        resolved.versionNo()
                ),
                () -> assertEquals(
                        "review-summary:v2",
                        resolved.versionTag()
                ),
                () -> assertEquals(
                        "Runtime review summary system prompt.",
                        resolved.content()
                )
        );
    }

    @Test
    void shouldUseFallbackWhenNoPromptVersionIsActive() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", null);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        assertTrue(
                promptManagementService.resolveActivePrompt(
                        PromptScene.REVIEW_SUMMARY
                ).isEmpty()
        );

        verify(
                promptVersionMapper,
                never()
        ).selectById(any());
    }

    @Test
    void shouldUseFallbackWhenActiveVersionBelongsToAnotherScene() {
        PromptDefinition definition =
                definition(3L, "REVIEW_SUMMARY", 101L);

        PromptVersion invalidVersion =
                version(101L, 4L, 2);

        when(promptDefinitionMapper.selectOne(any()))
                .thenReturn(definition);

        when(promptVersionMapper.selectById(101L))
                .thenReturn(invalidVersion);

        assertTrue(
                promptManagementService.resolveActivePrompt(
                        PromptScene.REVIEW_SUMMARY
                ).isEmpty()
        );
    }

    private PromptDefinition definition(
            Long id,
            String sceneCode,
            Long activeVersionId
    ) {
        PromptDefinition definition =
                new PromptDefinition();

        definition.setId(id);
        definition.setSceneCode(sceneCode);
        definition.setSceneName("评价摘要");
        definition.setDescription(
                "Generates structured review summaries."
        );
        definition.setActiveVersionId(activeVersionId);
        definition.setStatus("ACTIVE");
        definition.setCreatedAt(
                OffsetDateTime.parse(
                        "2026-07-21T17:00:00+08:00"
                )
        );
        definition.setUpdatedAt(
                OffsetDateTime.parse(
                        "2026-07-21T17:00:00+08:00"
                )
        );

        return definition;
    }

    private PromptVersion version(
            Long id,
            Long definitionId,
            int versionNo
    ) {
        PromptVersion version =
                new PromptVersion();

        version.setId(id);
        version.setPromptDefinitionId(definitionId);
        version.setVersionNo(versionNo);
        version.setVersionTag(
                "review-summary:v" + versionNo
        );
        version.setContent(
                "Review summary prompt version " + versionNo
        );
        version.setChangeNote(
                "Create version " + versionNo
        );
        version.setCreatedBy(1L);
        version.setCreatedAt(
                OffsetDateTime.parse(
                        "2026-07-21T17:00:00+08:00"
                )
        );

        return version;
    }
}