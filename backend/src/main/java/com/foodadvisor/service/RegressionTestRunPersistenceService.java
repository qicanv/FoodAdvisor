package com.foodadvisor.service;

import com.foodadvisor.entity.RegressionTestCaseResult;
import com.foodadvisor.entity.RegressionTestRun;
import com.foodadvisor.entity.RegressionTestRunSet;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.RegressionTestCaseResultMapper;
import com.foodadvisor.mapper.RegressionTestRunMapper;
import com.foodadvisor.mapper.RegressionTestRunSetMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegressionTestRunPersistenceService {

    private final RegressionTestRunMapper runMapper;
    private final RegressionTestRunSetMapper runSetMapper;
    private final RegressionTestCaseResultMapper caseResultMapper;

    public RegressionTestRunPersistenceService(
            RegressionTestRunMapper runMapper,
            RegressionTestRunSetMapper runSetMapper,
            RegressionTestCaseResultMapper caseResultMapper
    ) {
        this.runMapper = runMapper;
        this.runSetMapper = runSetMapper;
        this.caseResultMapper = caseResultMapper;
    }

    /**
     * 创建回归任务以及任务关联的全部测试集。
     *
     * 使用独立事务，保证任务初始化完成后，
     * 后续执行失败不会删除已经创建的任务记录。
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public RegressionTestRun createRunWithSets(
            RegressionTestRun run,
            List<RegressionTestRunSet> runSets
    ) {
        if (run == null) {
            throw persistenceFailure(
                    "REGRESSION_RUN_REQUIRED",
                    "回归测试任务不能为空"
            );
        }

        if (runMapper.insert(run) != 1) {
            throw persistenceFailure(
                    "REGRESSION_RUN_CREATE_FAILED",
                    "回归测试任务创建失败"
            );
        }

        if (runSets != null) {
            for (RegressionTestRunSet runSet : runSets) {
                runSet.setRunId(run.getId());

                if (runSetMapper.insert(runSet) != 1) {
                    throw persistenceFailure(
                            "REGRESSION_RUN_SET_CREATE_FAILED",
                            "回归测试任务关联测试集创建失败"
                    );
                }
            }
        }

        return run;
    }

    /**
     * 创建案例运行记录。
     *
     * 案例开始执行前先保存 RUNNING 记录。
     * 即使进程在案例执行过程中异常退出，也能够保留运行痕迹。
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public RegressionTestCaseResult createCaseResult(
            RegressionTestCaseResult result
    ) {
        requireCaseResultIdNotPresent(result);

        if (caseResultMapper.insert(result) != 1) {
            throw persistenceFailure(
                    "REGRESSION_CASE_RESULT_CREATE_FAILED",
                    "回归测试案例结果创建失败"
            );
        }

        return result;
    }

    /**
     * 更新案例执行结果。
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void updateCaseResult(
            RegressionTestCaseResult result
    ) {
        requirePositiveId(
                result == null ? null : result.getId(),
                "REGRESSION_CASE_RESULT_ID_REQUIRED",
                "案例结果ID不能为空"
        );

        if (caseResultMapper.updateById(result) != 1) {
            throw persistenceFailure(
                    "REGRESSION_CASE_RESULT_UPDATE_FAILED",
                    "回归测试案例结果更新失败"
            );
        }
    }

    /**
     * 更新某个测试集的执行进度。
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void updateRunSet(
            RegressionTestRunSet runSet
    ) {
        requirePositiveId(
                runSet == null ? null : runSet.getId(),
                "REGRESSION_RUN_SET_ID_REQUIRED",
                "任务测试集ID不能为空"
        );

        if (runSetMapper.updateById(runSet) != 1) {
            throw persistenceFailure(
                    "REGRESSION_RUN_SET_UPDATE_FAILED",
                    "回归测试集运行进度更新失败"
            );
        }
    }

    /**
     * 更新整个回归任务的执行进度。
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void updateRun(
            RegressionTestRun run
    ) {
        requirePositiveId(
                run == null ? null : run.getId(),
                "REGRESSION_RUN_ID_REQUIRED",
                "回归测试任务ID不能为空"
        );

        if (runMapper.updateById(run) != 1) {
            throw persistenceFailure(
                    "REGRESSION_RUN_UPDATE_FAILED",
                    "回归测试任务进度更新失败"
            );
        }
    }

    /**
     * 独立查询最新的任务记录。
     */
    public RegressionTestRun getRun(Long runId) {
        requirePositiveId(
                runId,
                "INVALID_REGRESSION_RUN_ID",
                "回归测试任务ID必须为正整数"
        );

        RegressionTestRun run =
                runMapper.selectById(runId);

        if (run == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REGRESSION_RUN_NOT_FOUND",
                    "回归测试任务不存在"
            );
        }

        return run;
    }

    /**
     * 独立查询任务中的测试集进度。
     */
    public RegressionTestRunSet getRunSet(
            Long runSetId
    ) {
        requirePositiveId(
                runSetId,
                "INVALID_REGRESSION_RUN_SET_ID",
                "任务测试集ID必须为正整数"
        );

        RegressionTestRunSet runSet =
                runSetMapper.selectById(runSetId);

        if (runSet == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REGRESSION_RUN_SET_NOT_FOUND",
                    "回归任务中的测试集记录不存在"
            );
        }

        return runSet;
    }

    /**
     * 独立查询案例结果。
     */
    public RegressionTestCaseResult getCaseResult(
            Long resultId
    ) {
        requirePositiveId(
                resultId,
                "INVALID_REGRESSION_CASE_RESULT_ID",
                "案例结果ID必须为正整数"
        );

        RegressionTestCaseResult result =
                caseResultMapper.selectById(resultId);

        if (result == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REGRESSION_CASE_RESULT_NOT_FOUND",
                    "回归测试案例结果不存在"
            );
        }

        return result;
    }

    private void requireCaseResultIdNotPresent(
            RegressionTestCaseResult result
    ) {
        if (result == null) {
            throw persistenceFailure(
                    "REGRESSION_CASE_RESULT_REQUIRED",
                    "案例结果不能为空"
            );
        }

        if (result.getId() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_CASE_RESULT_ALREADY_PERSISTED",
                    "已经保存的案例结果不能重复创建"
            );
        }
    }

    private void requirePositiveId(
            Long id,
            String errorCode,
            String message
    ) {
        if (id == null || id <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    errorCode,
                    message
            );
        }
    }

    private ApiException persistenceFailure(
            String errorCode,
            String message
    ) {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                errorCode,
                message
        );
    }
}