package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ReviewReplyDraft;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评价辅助回复草稿的 MyBatis-Plus Mapper（EPIC-02 故事7）
 *
 * 继承 BaseMapper 后自动获得基本的 CRUD 方法（insert、selectById、updateById 等），
 * 无需编写 XML 映射文件。
 */
@Mapper
public interface ReviewReplyDraftMapper extends BaseMapper<ReviewReplyDraft> {
}
