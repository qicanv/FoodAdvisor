package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.prompt.CreatePromptVersionRequest;
import com.foodadvisor.dto.prompt.PromptActivationLogResponse;
import com.foodadvisor.dto.prompt.PromptDefinitionResponse;
import com.foodadvisor.dto.prompt.PromptVersionResponse;
import com.foodadvisor.dto.prompt.PromptVersionSwitchRequest;
import com.foodadvisor.dto.prompt.ResolvedPrompt;
import com.foodadvisor.entity.PromptActivationLog;
import com.foodadvisor.entity.PromptDefinition;
import com.foodadvisor.entity.PromptVersion;
import com.foodadvisor.enums.PromptOperationType;
import com.foodadvisor.enums.PromptScene;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.PromptActivationLogMapper;
import com.foodadvisor.mapper.PromptDefinitionMapper;
import com.foodadvisor.mapper.PromptVersionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Optional;

@Service
public class PromptManagementService {

    private final PromptDefinitionMapper promptDefinitionMapper;
    private final PromptVersionMapper promptVersionMapper;
    private final PromptActivationLogMapper promptActivationLogMapper;

    public PromptManagementService(
            PromptDefinitionMapper promptDefinitionMapper,
            PromptVersionMapper promptVersionMapper,
            PromptActivationLogMapper promptActivationLogMapper
    ) {
        this.promptDefinitionMapper = promptDefinitionMapper;
        this.promptVersionMapper = promptVersionMapper;
        this.promptActivationLogMapper = promptActivationLogMapper;
    }

    /**
     * 获取指定场景当前启用的提示词。
     *
     * 该方法供业务运行时调用：
     * - 没有配置场景时返回 empty
     * - 场景被停用时返回 empty
     * - 没有启用版本时返回 empty
     * - activeVersionId 指向错误场景时返回 empty
     *
     * 返回 empty 后，调用方继续使用代码中的默认提示词，
     * 避免提示词管理配置缺失导致原有 AI 功能不可用。
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedPrompt> resolveActivePrompt(
            PromptScene scene
    ) {
        if (scene == null) {
            return Optional.empty();
        }

        PromptDefinition definition =
                promptDefinitionMapper.selectOne(
                        new LambdaQueryWrapper<PromptDefinition>()
                                .eq(
                                        PromptDefinition::getSceneCode,
                                        scene.getCode()
                                )
                );

        if (definition == null) {
            return Optional.empty();
        }

        if (!"ACTIVE".equalsIgnoreCase(definition.getStatus())) {
            return Optional.empty();
        }

        if (definition.getActiveVersionId() == null) {
            return Optional.empty();
        }

        PromptVersion version =
                promptVersionMapper.selectById(
                        definition.getActiveVersionId()
                );

        if (version == null) {
            return Optional.empty();
        }

        if (!Objects.equals(
                version.getPromptDefinitionId(),
                definition.getId()
        )) {
            return Optional.empty();
        }

        if (version.getContent() == null
                || version.getContent().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(
                new ResolvedPrompt(
                        definition.getId(),
                        version.getId(),
                        definition.getSceneCode(),
                        version.getVersionNo(),
                        version.getVersionTag(),
                        version.getContent()
                )
        );
    }

    /**
     * 查询全部提示词场景及其当前启用版本。
     */
    public List<PromptDefinitionResponse> listDefinitions() {
        List<PromptDefinition> definitions =
                promptDefinitionMapper.selectList(
                        new LambdaQueryWrapper<PromptDefinition>()
                                .orderByAsc(PromptDefinition::getId)
                );

        Set<Long> activeVersionIds = definitions.stream()
                .map(PromptDefinition::getActiveVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, PromptVersion> activeVersions =
                loadVersionsByIds(activeVersionIds);

        return definitions.stream()
                .map(definition -> toDefinitionResponse(
                        definition,
                        activeVersions.get(definition.getActiveVersionId())
                ))
                .toList();
    }

    /**
     * 查询单个场景详情。
     */
    public PromptDefinitionResponse getDefinition(String sceneCode) {
        PromptDefinition definition =
                getDefinitionEntityOrThrow(sceneCode);

        PromptVersion activeVersion = null;
        if (definition.getActiveVersionId() != null) {
            activeVersion = promptVersionMapper.selectById(
                    definition.getActiveVersionId()
            );
        }

        return toDefinitionResponse(definition, activeVersion);
    }

    /**
     * 查询场景的全部历史版本。
     */
    public List<PromptVersionResponse> listVersions(String sceneCode) {
        PromptDefinition definition =
                getDefinitionEntityOrThrow(sceneCode);

        return promptVersionMapper.selectList(
                        new LambdaQueryWrapper<PromptVersion>()
                                .eq(
                                        PromptVersion::getPromptDefinitionId,
                                        definition.getId()
                                )
                                .orderByDesc(PromptVersion::getVersionNo)
                )
                .stream()
                .map(version -> toVersionResponse(
                        version,
                        definition.getSceneCode(),
                        Objects.equals(
                                definition.getActiveVersionId(),
                                version.getId()
                        )
                ))
                .toList();
    }

    /**
     * 创建一个不可覆盖的新版本。
     */
    @Transactional
    public PromptVersionResponse createVersion(
            String sceneCode,
            CreatePromptVersionRequest request,
            Long operatorUserId
    ) {
        PromptDefinition definition =
                getDefinitionEntityOrThrow(sceneCode);

        validateCreateRequest(request);

        PromptVersion latestVersion =
                promptVersionMapper.selectOne(
                        new LambdaQueryWrapper<PromptVersion>()
                                .eq(
                                        PromptVersion::getPromptDefinitionId,
                                        definition.getId()
                                )
                                .orderByDesc(PromptVersion::getVersionNo)
                                .last("LIMIT 1")
                );

        int nextVersionNo = latestVersion == null
                ? 1
                : latestVersion.getVersionNo() + 1;

        PromptVersion version = new PromptVersion();
        version.setPromptDefinitionId(definition.getId());
        version.setVersionNo(nextVersionNo);
        version.setVersionTag(createVersionTag(
                definition.getSceneCode(),
                nextVersionNo
        ));
        version.setContent(request.content());
        version.setChangeNote(request.changeNote().trim());
        version.setCreatedBy(operatorUserId);
        version.setCreatedAt(OffsetDateTime.now());

        promptVersionMapper.insert(version);

        boolean active = false;
        if (request.activate()) {
            switchActiveVersion(
                    definition,
                    version,
                    PromptOperationType.ACTIVATE,
                    request.changeNote(),
                    operatorUserId
            );
            active = true;
        }

        return toVersionResponse(
                version,
                definition.getSceneCode(),
                active
        );
    }

    /**
     * 启用指定版本。
     */
    @Transactional
    public PromptVersionResponse activateVersion(
            String sceneCode,
            Long versionId,
            PromptVersionSwitchRequest request,
            Long operatorUserId
    ) {
        PromptDefinition definition =
                getDefinitionEntityOrThrow(sceneCode);

        PromptVersion version =
                getVersionForDefinitionOrThrow(
                        definition,
                        versionId
                );

        if (Objects.equals(
                definition.getActiveVersionId(),
                version.getId()
        )) {
            return toVersionResponse(
                    version,
                    definition.getSceneCode(),
                    true
            );
        }

        switchActiveVersion(
                definition,
                version,
                PromptOperationType.ACTIVATE,
                operationNote(request),
                operatorUserId
        );

        return toVersionResponse(
                version,
                definition.getSceneCode(),
                true
        );
    }

    /**
     * 回滚到历史版本。
     */
    @Transactional
    public PromptVersionResponse rollbackVersion(
            String sceneCode,
            Long versionId,
            PromptVersionSwitchRequest request,
            Long operatorUserId
    ) {
        PromptDefinition definition =
                getDefinitionEntityOrThrow(sceneCode);

        if (definition.getActiveVersionId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROMPT_ACTIVE_VERSION_NOT_FOUND",
                    "No active prompt version exists for this scene"
            );
        }

        PromptVersion currentVersion =
                promptVersionMapper.selectById(
                        definition.getActiveVersionId()
                );

        if (currentVersion == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROMPT_ACTIVE_VERSION_INVALID",
                    "The active prompt version does not exist"
            );
        }

        PromptVersion targetVersion =
                getVersionForDefinitionOrThrow(
                        definition,
                        versionId
                );

        if (Objects.equals(
                currentVersion.getId(),
                targetVersion.getId()
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PROMPT_VERSION_ALREADY_ACTIVE",
                    "The selected prompt version is already active"
            );
        }

        if (targetVersion.getVersionNo()
                >= currentVersion.getVersionNo()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROMPT_ROLLBACK_TARGET",
                    "Rollback target must be older than the active version"
            );
        }

        switchActiveVersion(
                definition,
                targetVersion,
                PromptOperationType.ROLLBACK,
                operationNote(request),
                operatorUserId
        );

        return toVersionResponse(
                targetVersion,
                definition.getSceneCode(),
                true
        );
    }

    /**
     * 查询启用和回滚历史。
     */
    public List<PromptActivationLogResponse> listActivationLogs(
            String sceneCode
    ) {
        PromptDefinition definition =
                getDefinitionEntityOrThrow(sceneCode);

        List<PromptActivationLog> logs =
                promptActivationLogMapper.selectList(
                        new LambdaQueryWrapper<PromptActivationLog>()
                                .eq(
                                        PromptActivationLog
                                                ::getPromptDefinitionId,
                                        definition.getId()
                                )
                                .orderByDesc(
                                        PromptActivationLog::getOperatedAt
                                )
                                .orderByDesc(
                                        PromptActivationLog::getId
                                )
                );

        Set<Long> versionIds = logs.stream()
                .flatMap(log -> Stream.of(
                        log.getFromVersionId(),
                        log.getToVersionId()
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, PromptVersion> versionById =
                loadVersionsByIds(versionIds);

        return logs.stream()
                .map(log -> {
                    PromptVersion fromVersion =
                            versionById.get(log.getFromVersionId());
                    PromptVersion toVersion =
                            versionById.get(log.getToVersionId());

                    return new PromptActivationLogResponse(
                            log.getId(),
                            definition.getSceneCode(),
                            log.getFromVersionId(),
                            fromVersion == null
                                    ? null
                                    : fromVersion.getVersionTag(),
                            log.getToVersionId(),
                            toVersion == null
                                    ? null
                                    : toVersion.getVersionTag(),
                            log.getOperationType(),
                            log.getOperationNote(),
                            log.getOperatedBy(),
                            log.getOperatedAt()
                    );
                })
                .toList();
    }

    private void switchActiveVersion(
            PromptDefinition definition,
            PromptVersion targetVersion,
            PromptOperationType operationType,
            String operationNote,
            Long operatorUserId
    ) {
        Long fromVersionId = definition.getActiveVersionId();
        OffsetDateTime now = OffsetDateTime.now();

        definition.setActiveVersionId(targetVersion.getId());
        definition.setUpdatedAt(now);
        promptDefinitionMapper.updateById(definition);

        PromptActivationLog log = new PromptActivationLog();
        log.setPromptDefinitionId(definition.getId());
        log.setFromVersionId(fromVersionId);
        log.setToVersionId(targetVersion.getId());
        log.setOperationType(operationType.name());
        log.setOperationNote(normalizeNullable(operationNote));
        log.setOperatedBy(operatorUserId);
        log.setOperatedAt(now);

        promptActivationLogMapper.insert(log);
    }

    private PromptDefinition getDefinitionEntityOrThrow(
            String sceneCode
    ) {
        String normalizedSceneCode = normalizeSceneCode(sceneCode);

        PromptDefinition definition =
                promptDefinitionMapper.selectOne(
                        new LambdaQueryWrapper<PromptDefinition>()
                                .eq(
                                        PromptDefinition::getSceneCode,
                                        normalizedSceneCode
                                )
                );

        if (definition == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PROMPT_DEFINITION_NOT_FOUND",
                    "Prompt definition not found"
            );
        }

        return definition;
    }

    private PromptVersion getVersionForDefinitionOrThrow(
            PromptDefinition definition,
            Long versionId
    ) {
        if (versionId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROMPT_VERSION_ID_REQUIRED",
                    "Prompt version id is required"
            );
        }

        PromptVersion version =
                promptVersionMapper.selectById(versionId);

        if (version == null
                || !Objects.equals(
                        version.getPromptDefinitionId(),
                        definition.getId()
                )) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PROMPT_VERSION_NOT_FOUND",
                    "Prompt version not found for this scene"
            );
        }

        return version;
    }

    private String normalizeSceneCode(String sceneCode) {
        try {
            return PromptScene.fromCode(sceneCode).getCode();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROMPT_SCENE",
                    exception.getMessage()
            );
        }
    }

    private void validateCreateRequest(
            CreatePromptVersionRequest request
    ) {
        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROMPT_VERSION_REQUEST_REQUIRED",
                    "Prompt version request is required"
            );
        }

        if (request.content() == null
                || request.content().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROMPT_CONTENT_REQUIRED",
                    "Prompt content must not be blank"
            );
        }

        if (request.changeNote() == null
                || request.changeNote().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PROMPT_CHANGE_NOTE_REQUIRED",
                    "Prompt change note must not be blank"
            );
        }
    }

    private String createVersionTag(
            String sceneCode,
            int versionNo
    ) {
        return sceneCode
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                + ":v"
                + versionNo;
    }

    private String operationNote(
            PromptVersionSwitchRequest request
    ) {
        if (request == null) {
            return null;
        }
        return request.operationNote();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Map<Long, PromptVersion> loadVersionsByIds(
            Set<Long> versionIds
    ) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PromptVersion> versionById = new HashMap<>();
        for (PromptVersion version :
                promptVersionMapper.selectBatchIds(versionIds)) {
            versionById.put(version.getId(), version);
        }
        return versionById;
    }

    private PromptDefinitionResponse toDefinitionResponse(
            PromptDefinition definition,
            PromptVersion activeVersion
    ) {
        return new PromptDefinitionResponse(
                definition.getId(),
                definition.getSceneCode(),
                definition.getSceneName(),
                definition.getDescription(),
                definition.getStatus(),
                activeVersion == null
                        ? null
                        : activeVersion.getId(),
                activeVersion == null
                        ? null
                        : activeVersion.getVersionNo(),
                activeVersion == null
                        ? null
                        : activeVersion.getVersionTag(),
                activeVersion == null
                        ? null
                        : activeVersion.getContent(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }

    private PromptVersionResponse toVersionResponse(
            PromptVersion version,
            String sceneCode,
            boolean active
    ) {
        return new PromptVersionResponse(
                version.getId(),
                version.getPromptDefinitionId(),
                sceneCode,
                version.getVersionNo(),
                version.getVersionTag(),
                version.getContent(),
                version.getChangeNote(),
                version.getCreatedBy(),
                version.getCreatedAt(),
                active
        );
    }
}