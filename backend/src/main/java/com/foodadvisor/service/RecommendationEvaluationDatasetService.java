package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.evaluation.RecommendationEvalCaseRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalCaseResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalDatasetRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalDatasetResponse;
import com.foodadvisor.entity.RecommendationEvalCase;
import com.foodadvisor.entity.RecommendationEvalDataset;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.RecommendationEvalCaseMapper;
import com.foodadvisor.mapper.RecommendationEvalDatasetMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RecommendationEvaluationDatasetService {

    private static final Set<String> DATASET_STATUSES = Set.of(
            "DRAFT",
            "ACTIVE",
            "ARCHIVED"
    );

    private final RecommendationEvalDatasetMapper datasetMapper;
    private final RecommendationEvalCaseMapper caseMapper;
    private final ObjectMapper objectMapper;

    public RecommendationEvaluationDatasetService(
            RecommendationEvalDatasetMapper datasetMapper,
            RecommendationEvalCaseMapper caseMapper,
            ObjectMapper objectMapper
    ) {
        this.datasetMapper = datasetMapper;
        this.caseMapper = caseMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询评测测试集。
     */
    public Page<RecommendationEvalDatasetResponse> queryDatasets(
            long pageNum,
            long pageSize,
            String status,
            String keyword
    ) {
        validatePage(pageNum, pageSize);

        LambdaQueryWrapper<RecommendationEvalDataset> wrapper =
                new LambdaQueryWrapper<>();

        if (hasText(status)) {
            String normalizedStatus = normalizeStatus(status);
            wrapper.eq(
                    RecommendationEvalDataset::getStatus,
                    normalizedStatus
            );
        }

        if (hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(query -> query
                    .like(
                            RecommendationEvalDataset::getName,
                            normalizedKeyword
                    )
                    .or()
                    .like(
                            RecommendationEvalDataset::getDescription,
                            normalizedKeyword
                    ));
        }

        wrapper.orderByDesc(RecommendationEvalDataset::getUpdatedAt)
                .orderByDesc(RecommendationEvalDataset::getId);

        Page<RecommendationEvalDataset> entityPage =
                datasetMapper.selectPage(
                        Page.of(pageNum, pageSize),
                        wrapper
                );

        Page<RecommendationEvalDatasetResponse> responsePage =
                new Page<>(
                        entityPage.getCurrent(),
                        entityPage.getSize(),
                        entityPage.getTotal()
                );

        responsePage.setRecords(
                entityPage.getRecords()
                        .stream()
                        .map(this::toDatasetResponse)
                        .toList()
        );

        return responsePage;
    }

    /**
     * 查询单个测试集。
     */
    public RecommendationEvalDatasetResponse getDataset(Long datasetId) {
        return toDatasetResponse(getDatasetOrThrow(datasetId));
    }

    /**
     * 创建测试集。
     */
    @Transactional
    public RecommendationEvalDatasetResponse createDataset(
            RecommendationEvalDatasetRequest request,
            Long createdBy
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        RecommendationEvalDataset dataset =
                new RecommendationEvalDataset();

        dataset.setName(request.name().trim());
        dataset.setDescription(cleanNullable(request.description()));
        dataset.setDataVersion(cleanNullable(request.dataVersion()));
        dataset.setStatus(
                hasText(request.status())
                        ? normalizeStatus(request.status())
                        : "DRAFT"
        );
        dataset.setCreatedBy(createdBy);
        dataset.setCreatedAt(now);
        dataset.setUpdatedAt(now);

        if (datasetMapper.insert(dataset) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_DATASET_CREATE_FAILED",
                    "Failed to create recommendation evaluation dataset"
            );
        }

        return toDatasetResponse(dataset);
    }

    /**
     * 更新测试集。
     */
    @Transactional
    public RecommendationEvalDatasetResponse updateDataset(
            Long datasetId,
            RecommendationEvalDatasetRequest request
    ) {
        RecommendationEvalDataset dataset =
                getDatasetOrThrow(datasetId);

        dataset.setName(request.name().trim());
        dataset.setDescription(cleanNullable(request.description()));
        dataset.setDataVersion(cleanNullable(request.dataVersion()));

        if (hasText(request.status())) {
            dataset.setStatus(normalizeStatus(request.status()));
        }

        dataset.setUpdatedAt(OffsetDateTime.now());

        if (datasetMapper.updateById(dataset) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_DATASET_UPDATE_FAILED",
                    "Failed to update recommendation evaluation dataset"
            );
        }

        return toDatasetResponse(getDatasetOrThrow(datasetId));
    }

    /**
     * 查询测试集下的案例。
     */
    public List<RecommendationEvalCaseResponse> listCases(
            Long datasetId,
            Boolean enabled
    ) {
        getDatasetOrThrow(datasetId);

        LambdaQueryWrapper<RecommendationEvalCase> wrapper =
                new LambdaQueryWrapper<RecommendationEvalCase>()
                        .eq(
                                RecommendationEvalCase::getDatasetId,
                                datasetId
                        );

        if (enabled != null) {
            wrapper.eq(
                    RecommendationEvalCase::getEnabled,
                    enabled
            );
        }

        wrapper.orderByAsc(RecommendationEvalCase::getSequenceNo)
                .orderByAsc(RecommendationEvalCase::getId);

        return caseMapper.selectList(wrapper)
                .stream()
                .map(this::toCaseResponse)
                .toList();
    }

    /**
     * 查询单个案例。
     */
    public RecommendationEvalCaseResponse getCase(
            Long datasetId,
            Long caseId
    ) {
        return toCaseResponse(
                getCaseOrThrow(datasetId, caseId)
        );
    }

    /**
     * 创建测试案例。
     */
    @Transactional
    public RecommendationEvalCaseResponse createCase(
            Long datasetId,
            RecommendationEvalCaseRequest request
    ) {
        getDatasetOrThrow(datasetId);

        String caseCode = normalizeCaseCode(request.caseCode());
        ensureCaseCodeUnique(datasetId, caseCode, null);

        OffsetDateTime now = OffsetDateTime.now();

        RecommendationEvalCase evalCase =
                new RecommendationEvalCase();

        evalCase.setDatasetId(datasetId);
        evalCase.setCaseCode(caseCode);
        evalCase.setCaseName(cleanNullable(request.caseName()));
        evalCase.setInputText(request.inputText().trim());
        evalCase.setExpectedConstraints(
                requireJsonObject(
                        request.expectedConstraints(),
                        "expectedConstraints"
                )
        );
        evalCase.setLocationSnapshot(
                optionalJsonObject(
                        request.locationSnapshot(),
                        "locationSnapshot"
                )
        );
        evalCase.setTags(
                optionalJsonArray(
                        request.tags(),
                        "tags"
                )
        );
        evalCase.setSequenceNo(
                request.sequenceNo() == null
                        ? 0
                        : request.sequenceNo()
        );
        evalCase.setEnabled(
                request.enabled() == null
                        ? Boolean.TRUE
                        : request.enabled()
        );
        evalCase.setCreatedAt(now);
        evalCase.setUpdatedAt(now);

        if (caseMapper.insert(evalCase) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_CASE_CREATE_FAILED",
                    "Failed to create recommendation evaluation case"
            );
        }

        return toCaseResponse(evalCase);
    }

    /**
     * 更新测试案例。
     */
    @Transactional
    public RecommendationEvalCaseResponse updateCase(
            Long datasetId,
            Long caseId,
            RecommendationEvalCaseRequest request
    ) {
        RecommendationEvalCase evalCase =
                getCaseOrThrow(datasetId, caseId);

        String caseCode = normalizeCaseCode(request.caseCode());
        ensureCaseCodeUnique(datasetId, caseCode, caseId);

        evalCase.setCaseCode(caseCode);
        evalCase.setCaseName(cleanNullable(request.caseName()));
        evalCase.setInputText(request.inputText().trim());
        evalCase.setExpectedConstraints(
                requireJsonObject(
                        request.expectedConstraints(),
                        "expectedConstraints"
                )
        );
        evalCase.setLocationSnapshot(
                optionalJsonObject(
                        request.locationSnapshot(),
                        "locationSnapshot"
                )
        );
        evalCase.setTags(
                optionalJsonArray(
                        request.tags(),
                        "tags"
                )
        );
        evalCase.setSequenceNo(
                request.sequenceNo() == null
                        ? 0
                        : request.sequenceNo()
        );
        evalCase.setEnabled(
                request.enabled() == null
                        ? Boolean.TRUE
                        : request.enabled()
        );
        evalCase.setUpdatedAt(OffsetDateTime.now());

        if (caseMapper.updateById(evalCase) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_CASE_UPDATE_FAILED",
                    "Failed to update recommendation evaluation case"
            );
        }

        return toCaseResponse(
                getCaseOrThrow(datasetId, caseId)
        );
    }

    /**
     * 删除测试案例。
     */
    @Transactional
    public void deleteCase(Long datasetId, Long caseId) {
        RecommendationEvalCase evalCase =
                getCaseOrThrow(datasetId, caseId);

        if (caseMapper.deleteById(evalCase.getId()) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_CASE_DELETE_FAILED",
                    "Failed to delete recommendation evaluation case"
            );
        }
    }

    private RecommendationEvalDataset getDatasetOrThrow(Long datasetId) {
        if (datasetId == null || datasetId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVAL_DATASET_ID",
                    "Evaluation dataset id must be positive"
            );
        }

        RecommendationEvalDataset dataset =
                datasetMapper.selectById(datasetId);

        if (dataset == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EVAL_DATASET_NOT_FOUND",
                    "Recommendation evaluation dataset not found"
            );
        }

        return dataset;
    }

    private RecommendationEvalCase getCaseOrThrow(
            Long datasetId,
            Long caseId
    ) {
        if (caseId == null || caseId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVAL_CASE_ID",
                    "Evaluation case id must be positive"
            );
        }

        RecommendationEvalCase evalCase =
                caseMapper.selectOne(
                        new LambdaQueryWrapper<RecommendationEvalCase>()
                                .eq(
                                        RecommendationEvalCase::getId,
                                        caseId
                                )
                                .eq(
                                        RecommendationEvalCase::getDatasetId,
                                        datasetId
                                )
                );

        if (evalCase == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EVAL_CASE_NOT_FOUND",
                    "Recommendation evaluation case not found"
            );
        }

        return evalCase;
    }

    private void ensureCaseCodeUnique(
            Long datasetId,
            String caseCode,
            Long excludedCaseId
    ) {
        LambdaQueryWrapper<RecommendationEvalCase> wrapper =
                new LambdaQueryWrapper<RecommendationEvalCase>()
                        .eq(
                                RecommendationEvalCase::getDatasetId,
                                datasetId
                        )
                        .eq(
                                RecommendationEvalCase::getCaseCode,
                                caseCode
                        );

        if (excludedCaseId != null) {
            wrapper.ne(
                    RecommendationEvalCase::getId,
                    excludedCaseId
            );
        }

        if (caseMapper.selectCount(wrapper) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EVAL_CASE_CODE_DUPLICATE",
                    "Case code already exists in this evaluation dataset"
            );
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);

        if (!DATASET_STATUSES.contains(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVAL_DATASET_STATUS",
                    "Dataset status must be DRAFT, ACTIVE or ARCHIVED"
            );
        }

        return normalized;
    }

    private String normalizeCaseCode(String caseCode) {
        return caseCode.trim().toUpperCase(Locale.ROOT);
    }

    private String requireJsonObject(
            String rawValue,
            String fieldName
    ) {
        if (!hasText(rawValue)) {
            throw invalidJson(fieldName, "must be a JSON object");
        }

        JsonNode node = parseJson(rawValue, fieldName);

        if (!node.isObject()) {
            throw invalidJson(fieldName, "must be a JSON object");
        }

        return node.toString();
    }

    private String optionalJsonObject(
            String rawValue,
            String fieldName
    ) {
        if (!hasText(rawValue)) {
            return "{}";
        }

        JsonNode node = parseJson(rawValue, fieldName);

        if (!node.isObject()) {
            throw invalidJson(fieldName, "must be a JSON object");
        }

        return node.toString();
    }

    private String optionalJsonArray(
            String rawValue,
            String fieldName
    ) {
        if (!hasText(rawValue)) {
            return "[]";
        }

        JsonNode node = parseJson(rawValue, fieldName);

        if (!node.isArray()) {
            throw invalidJson(fieldName, "must be a JSON array");
        }

        return node.toString();
    }

    private JsonNode parseJson(
            String rawValue,
            String fieldName
    ) {
        try {
            return objectMapper.readTree(rawValue);
        } catch (JsonProcessingException exception) {
            throw invalidJson(fieldName, "contains invalid JSON");
        }
    }

    private ApiException invalidJson(
            String fieldName,
            String reason
    ) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EVAL_JSON",
                fieldName + " " + reason
        );
    }

    private void validatePage(long pageNum, long pageSize) {
        if (pageNum < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_NUM",
                    "pageNum must be at least 1"
            );
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_SIZE",
                    "pageSize must be between 1 and 100"
            );
        }
    }

    private RecommendationEvalDatasetResponse toDatasetResponse(
            RecommendationEvalDataset dataset
    ) {
        return new RecommendationEvalDatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getDescription(),
                dataset.getDataVersion(),
                dataset.getStatus(),
                dataset.getCreatedBy(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt()
        );
    }

    private RecommendationEvalCaseResponse toCaseResponse(
            RecommendationEvalCase evalCase
    ) {
        return new RecommendationEvalCaseResponse(
                evalCase.getId(),
                evalCase.getDatasetId(),
                evalCase.getCaseCode(),
                evalCase.getCaseName(),
                evalCase.getInputText(),
                evalCase.getExpectedConstraints(),
                evalCase.getLocationSnapshot(),
                evalCase.getTags(),
                evalCase.getSequenceNo(),
                evalCase.getEnabled(),
                evalCase.getCreatedAt(),
                evalCase.getUpdatedAt()
        );
    }

    private String cleanNullable(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}