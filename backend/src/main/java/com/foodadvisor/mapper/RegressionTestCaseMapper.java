package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.RegressionTestCase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RegressionTestCaseMapper
        extends BaseMapper<RegressionTestCase> {
}