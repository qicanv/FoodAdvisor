package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.RegionHotWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 区域热词 Mapper
 *
 * 提供对 region_hot_words 表的基础 CRUD 操作（继承自 MyBatis-Plus BaseMapper）。
 * 复杂查询（如按区域 + 热度排序）在 Service 层通过 LambdaQueryWrapper 构建。
 */
@Mapper
public interface RegionHotWordMapper extends BaseMapper<RegionHotWord> {
}
