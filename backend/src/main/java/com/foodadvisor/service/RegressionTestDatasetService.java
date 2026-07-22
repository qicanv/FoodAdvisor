package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.regression.RegressionTestCaseRequest;
import com.foodadvisor.dto.regression.RegressionTestCaseResponse;
import com.foodadvisor.dto.regression.RegressionTestSetRequest;
import com.foodadvisor.dto.regression.RegressionTestSetResponse;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.entity.RegressionTestSet;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.RegressionTestCaseMapper;
import com.foodadvisor.mapper.RegressionTestSetMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RegressionTestDatasetService {

    private static final Set<String> TEST_TYPES = Set.of(
            "CONSTRAINT_EXTRACTION",
            "RECOMMENDATION",
            "REVIEW_SUMMARY",
            "SENTIMENT_ANALYSIS"
    );

    private static final Set<String> TEST_SET_STATUSES = Set.of(
            "DRAFT",
            "ACTIVE",
            "ARCHIVED"
    );

    private final RegressionTestSetMapper testSetMapper;
    private final RegressionTestCaseMapper testCaseMapper;
    private final ObjectMapper objectMapper;

    public RegressionTestDatasetService(
            RegressionTestSetMapper testSetMapper,
            RegressionTestCaseMapper testCaseMapper,
            ObjectMapper objectMapper
    ) {
        this.testSetMapper = testSetMapper;
        this.testCaseMapper = testCaseMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询回归测试集。
     */
    public Page<RegressionTestSetResponse> queryTestSets(
            long pageNum,
            long pageSize,
            String status,
            String testType,
            String keyword
    ) {
        validatePage(pageNum, pageSize);

        LambdaQueryWrapper<RegressionTestSet> wrapper =
                new LambdaQueryWrapper<>();

        if (hasText(status)) {
            wrapper.eq(
                    RegressionTestSet::getStatus,
                    normalizeStatus(status)
            );
        }

        if (hasText(testType)) {
            wrapper.eq(
                    RegressionTestSet::getTestType,
                    normalizeTestType(testType)
            );
        }

        if (hasText(keyword)) {
            String normalizedKeyword = keyword.trim();

            wrapper.and(query -> query
                    .like(
                            RegressionTestSet::getName,
                            normalizedKeyword
                    )
                    .or()
                    .like(
                            RegressionTestSet::getDescription,
                            normalizedKeyword
                    ));
        }

        wrapper.orderByDesc(RegressionTestSet::getUpdatedAt)
                .orderByDesc(RegressionTestSet::getId);

        Page<RegressionTestSet> entityPage =
                testSetMapper.selectPage(
                        Page.of(pageNum, pageSize),
                        wrapper
                );

        Page<RegressionTestSetResponse> responsePage =
                new Page<>(
                        entityPage.getCurrent(),
                        entityPage.getSize(),
                        entityPage.getTotal()
                );

        responsePage.setRecords(
                entityPage.getRecords()
                        .stream()
                        .map(this::toTestSetResponse)
                        .toList()
        );

        return responsePage;
    }

    /**
     * 查询单个回归测试集。
     */
    public RegressionTestSetResponse getTestSet(Long testSetId) {
        return toTestSetResponse(
                getTestSetOrThrow(testSetId)
        );
    }

    /**
     * 创建回归测试集。
     */
    @Transactional
    public RegressionTestSetResponse createTestSet(
            RegressionTestSetRequest request,
            Long createdBy
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        RegressionTestSet testSet = new RegressionTestSet();

        testSet.setName(request.name().trim());
        testSet.setDescription(
                cleanNullable(request.description())
        );
        testSet.setTestType(
                normalizeTestType(request.testType())
        );
        testSet.setDataVersion(
                request.dataVersion().trim()
        );
        testSet.setStatus(
                normalizeStatus(request.status())
        );
        testSet.setMetadata(
                optionalJsonObject(
                        request.metadata(),
                        "metadata"
                )
        );
        testSet.setCreatedBy(createdBy);
        testSet.setCreatedAt(now);
        testSet.setUpdatedAt(now);

        if (testSetMapper.insert(testSet) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REGRESSION_TEST_SET_CREATE_FAILED",
                    "回归测试集创建失败"
            );
        }

        return toTestSetResponse(testSet);
    }

    /**
     * 修改回归测试集。
     */
    @Transactional
    public RegressionTestSetResponse updateTestSet(
            Long testSetId,
            RegressionTestSetRequest request
    ) {
        RegressionTestSet testSet =
                getTestSetOrThrow(testSetId);

        String newTestType =
                normalizeTestType(request.testType());

        if (!newTestType.equals(testSet.getTestType())
                && countCases(testSetId) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REGRESSION_TEST_TYPE_CHANGE_FORBIDDEN",
                    "已有测试案例的测试集不能修改测试类型"
            );
        }

        testSet.setName(request.name().trim());
        testSet.setDescription(
                cleanNullable(request.description())
        );
        testSet.setTestType(newTestType);
        testSet.setDataVersion(
                request.dataVersion().trim()
        );
        testSet.setStatus(
                normalizeStatus(request.status())
        );
        testSet.setMetadata(
                optionalJsonObject(
                        request.metadata(),
                        "metadata"
                )
        );
        testSet.setUpdatedAt(OffsetDateTime.now());

        if (testSetMapper.updateById(testSet) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REGRESSION_TEST_SET_UPDATE_FAILED",
                    "回归测试集修改失败"
            );
        }

        return toTestSetResponse(
                getTestSetOrThrow(testSetId)
        );
    }

    /**
     * 查询测试集中的测试案例。
     */
    public List<RegressionTestCaseResponse> listCases(
            Long testSetId,
            Boolean enabled
    ) {
        getTestSetOrThrow(testSetId);

        LambdaQueryWrapper<RegressionTestCase> wrapper =
                new LambdaQueryWrapper<RegressionTestCase>()
                        .eq(
                                RegressionTestCase::getTestSetId,
                                testSetId
                        );

        if (enabled != null) {
            wrapper.eq(
                    RegressionTestCase::getEnabled,
                    enabled
            );
        }

        wrapper.orderByAsc(
                        RegressionTestCase::getSequenceNo
                )
                .orderByAsc(
                        RegressionTestCase::getId
                );

        return testCaseMapper.selectList(wrapper)
                .stream()
                .map(this::toTestCaseResponse)
                .toList();
    }

    /**
     * 查询单个测试案例。
     */
    public RegressionTestCaseResponse getCase(
            Long testSetId,
            Long caseId
    ) {
        return toTestCaseResponse(
                getCaseOrThrow(testSetId, caseId)
        );
    }

    /**
     * 创建测试案例。
     */
    @Transactional
    public RegressionTestCaseResponse createCase(
            Long testSetId,
            RegressionTestCaseRequest request
    ) {
        getTestSetOrThrow(testSetId);

        String caseCode =
                normalizeCaseCode(request.caseCode());

        ensureCaseCodeUnique(
                testSetId,
                caseCode,
                null
        );

        OffsetDateTime now = OffsetDateTime.now();

        RegressionTestCase testCase =
                new RegressionTestCase();

        testCase.setTestSetId(testSetId);
        testCase.setCaseCode(caseCode);
        testCase.setCaseName(
                cleanNullable(request.caseName())
        );
        testCase.setDescription(
                cleanNullable(request.description())
        );
        testCase.setInputPayload(
                requireJsonObject(
                        request.inputPayload(),
                        "inputPayload"
                )
        );
        testCase.setExpectedOutput(
                requireJsonObject(
                        request.expectedOutput(),
                        "expectedOutput"
                )
        );
        testCase.setAssertionConfig(
                optionalJsonObject(
                        request.assertionConfig(),
                        "assertionConfig"
                )
        );
        testCase.setTags(
                optionalJsonArray(
                        request.tags(),
                        "tags"
                )
        );
        testCase.setSequenceNo(
                request.sequenceNo() == null
                        ? 0
                        : request.sequenceNo()
        );
        testCase.setEnabled(
                request.enabled() == null
                        ? Boolean.TRUE
                        : request.enabled()
        );
        testCase.setCreatedAt(now);
        testCase.setUpdatedAt(now);

        try {
            if (testCaseMapper.insert(testCase) != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "REGRESSION_TEST_CASE_CREATE_FAILED",
                        "回归测试案例创建失败"
                );
            }
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCaseCode();
        }

        return toTestCaseResponse(testCase);
    }

    /**
     * 修改测试案例。
     */
    @Transactional
    public RegressionTestCaseResponse updateCase(
            Long testSetId,
            Long caseId,
            RegressionTestCaseRequest request
    ) {
        RegressionTestCase testCase =
                getCaseOrThrow(testSetId, caseId);

        String caseCode =
                normalizeCaseCode(request.caseCode());

        ensureCaseCodeUnique(
                testSetId,
                caseCode,
                caseId
        );

        testCase.setCaseCode(caseCode);
        testCase.setCaseName(
                cleanNullable(request.caseName())
        );
        testCase.setDescription(
                cleanNullable(request.description())
        );
        testCase.setInputPayload(
                requireJsonObject(
                        request.inputPayload(),
                        "inputPayload"
                )
        );
        testCase.setExpectedOutput(
                requireJsonObject(
                        request.expectedOutput(),
                        "expectedOutput"
                )
        );
        testCase.setAssertionConfig(
                optionalJsonObject(
                        request.assertionConfig(),
                        "assertionConfig"
                )
        );
        testCase.setTags(
                optionalJsonArray(
                        request.tags(),
                        "tags"
                )
        );
        testCase.setSequenceNo(
                request.sequenceNo() == null
                        ? 0
                        : request.sequenceNo()
        );
        testCase.setEnabled(
                request.enabled() == null
                        ? Boolean.TRUE
                        : request.enabled()
        );
        testCase.setUpdatedAt(OffsetDateTime.now());

        try {
            if (testCaseMapper.updateById(testCase) != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "REGRESSION_TEST_CASE_UPDATE_FAILED",
                        "回归测试案例修改失败"
                );
            }
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCaseCode();
        }

        return toTestCaseResponse(
                getCaseOrThrow(testSetId, caseId)
        );
    }

    /**
     * 删除测试案例。
     */
    @Transactional
    public void deleteCase(
            Long testSetId,
            Long caseId
    ) {
        RegressionTestCase testCase =
                getCaseOrThrow(testSetId, caseId);

        try {
            if (testCaseMapper.deleteById(
                    testCase.getId()
            ) != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "REGRESSION_TEST_CASE_DELETE_FAILED",
                        "回归测试案例删除失败"
                );
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REGRESSION_TEST_CASE_IN_USE",
                    "该测试案例已有历史运行结果，不能删除"
            );
        }
    }

    private RegressionTestSet getTestSetOrThrow(
            Long testSetId
    ) {
        if (testSetId == null || testSetId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REGRESSION_TEST_SET_ID",
                    "测试集ID必须为正整数"
            );
        }

        RegressionTestSet testSet =
                testSetMapper.selectById(testSetId);

        if (testSet == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REGRESSION_TEST_SET_NOT_FOUND",
                    "回归测试集不存在"
            );
        }

        return testSet;
    }

    private RegressionTestCase getCaseOrThrow(
            Long testSetId,
            Long caseId
    ) {
        getTestSetOrThrow(testSetId);

        if (caseId == null || caseId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REGRESSION_TEST_CASE_ID",
                    "测试案例ID必须为正整数"
            );
        }

        RegressionTestCase testCase =
                testCaseMapper.selectOne(
                        new LambdaQueryWrapper<RegressionTestCase>()
                                .eq(
                                        RegressionTestCase::getId,
                                        caseId
                                )
                                .eq(
                                        RegressionTestCase::getTestSetId,
                                        testSetId
                                )
                );

        if (testCase == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REGRESSION_TEST_CASE_NOT_FOUND",
                    "回归测试案例不存在"
            );
        }

        return testCase;
    }

    private long countCases(Long testSetId) {
        return testCaseMapper.selectCount(
                new LambdaQueryWrapper<RegressionTestCase>()
                        .eq(
                                RegressionTestCase::getTestSetId,
                                testSetId
                        )
        );
    }

    private void ensureCaseCodeUnique(
            Long testSetId,
            String caseCode,
            Long excludedCaseId
    ) {
        LambdaQueryWrapper<RegressionTestCase> wrapper =
                new LambdaQueryWrapper<RegressionTestCase>()
                        .eq(
                                RegressionTestCase::getTestSetId,
                                testSetId
                        )
                        .eq(
                                RegressionTestCase::getCaseCode,
                                caseCode
                        );

        if (excludedCaseId != null) {
            wrapper.ne(
                    RegressionTestCase::getId,
                    excludedCaseId
            );
        }

        if (testCaseMapper.selectCount(wrapper) > 0) {
            throw duplicateCaseCode();
        }
    }

    private ApiException duplicateCaseCode() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "REGRESSION_TEST_CASE_CODE_DUPLICATE",
                "当前测试集中已存在相同案例编号"
        );
    }

    private String normalizeTestType(String testType) {
        String normalized =
                testType.trim().toUpperCase(Locale.ROOT);

        if (!TEST_TYPES.contains(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REGRESSION_TEST_TYPE",
                    "测试类型必须为需求提取、推荐、评价摘要或情感分析"
            );
        }

        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized =
                status.trim().toUpperCase(Locale.ROOT);

        if (!TEST_SET_STATUSES.contains(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REGRESSION_TEST_SET_STATUS",
                    "测试集状态必须为 DRAFT、ACTIVE 或 ARCHIVED"
            );
        }

        return normalized;
    }

    private String normalizeCaseCode(String caseCode) {
        return caseCode.trim().toUpperCase(Locale.ROOT);
    }

    private String requireJsonObject(
            JsonNode value,
            String fieldName
    ) {
        if (value == null || value.isNull()) {
            throw invalidJsonShape(
                    fieldName,
                    "不能为空"
            );
        }

        if (!value.isObject()) {
            throw invalidJsonShape(
                    fieldName,
                    "必须为JSON对象"
            );
        }

        return value.toString();
    }

    private String optionalJsonObject(
            JsonNode value,
            String fieldName
    ) {
        if (value == null || value.isNull()) {
            return "{}";
        }

        if (!value.isObject()) {
            throw invalidJsonShape(
                    fieldName,
                    "必须为JSON对象"
            );
        }

        return value.toString();
    }

    private String optionalJsonArray(
            JsonNode value,
            String fieldName
    ) {
        if (value == null || value.isNull()) {
            return "[]";
        }

        if (!value.isArray()) {
            throw invalidJsonShape(
                    fieldName,
                    "必须为JSON数组"
            );
        }

        return value.toString();
    }

    private ApiException invalidJsonShape(
            String fieldName,
            String reason
    ) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REGRESSION_TEST_JSON",
                fieldName + reason
        );
    }

    private RegressionTestSetResponse toTestSetResponse(
            RegressionTestSet testSet
    ) {
        return new RegressionTestSetResponse(
                testSet.getId(),
                testSet.getName(),
                testSet.getDescription(),
                testSet.getTestType(),
                testSet.getDataVersion(),
                testSet.getStatus(),
                parseStoredJson(
                        testSet.getMetadata(),
                        false
                ),
                testSet.getCreatedBy(),
                testSet.getCreatedAt(),
                testSet.getUpdatedAt()
        );
    }

    private RegressionTestCaseResponse toTestCaseResponse(
            RegressionTestCase testCase
    ) {
        return new RegressionTestCaseResponse(
                testCase.getId(),
                testCase.getTestSetId(),
                testCase.getCaseCode(),
                testCase.getCaseName(),
                testCase.getDescription(),
                parseStoredJson(
                        testCase.getInputPayload(),
                        false
                ),
                parseStoredJson(
                        testCase.getExpectedOutput(),
                        false
                ),
                parseStoredJson(
                        testCase.getAssertionConfig(),
                        false
                ),
                parseStoredJson(
                        testCase.getTags(),
                        true
                ),
                testCase.getSequenceNo(),
                testCase.getEnabled(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt()
        );
    }

    private JsonNode parseStoredJson(
            String rawJson,
            boolean arrayDefault
    ) {
        if (!hasText(rawJson)) {
            return arrayDefault
                    ? objectMapper.createArrayNode()
                    : objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REGRESSION_TEST_STORED_JSON_INVALID",
                    "数据库中保存的回归测试JSON无效"
            );
        }
    }

    private void validatePage(
            long pageNum,
            long pageSize
    ) {
        if (pageNum < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_NUM",
                    "pageNum不能小于1"
            );
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_SIZE",
                    "pageSize必须在1到100之间"
            );
        }
    }

    private String cleanNullable(String value) {
        return hasText(value)
                ? value.trim()
                : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}