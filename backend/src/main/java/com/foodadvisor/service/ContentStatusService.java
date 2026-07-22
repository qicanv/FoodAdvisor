package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.entity.ContentStatusHistory;
import com.foodadvisor.mapper.ContentStatusHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容状态变更历史服务
 * 负责记录和查询商家、菜品、专题等内容的状态变更历史
 */
@Service
public class ContentStatusService {

    private static final Logger log =
            LoggerFactory.getLogger(ContentStatusService.class);

    private final ContentStatusHistoryMapper mapper;

    public ContentStatusService(ContentStatusHistoryMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 记录一次状态变更。
     *
     * @param contentType    内容类型（MERCHANT / DISH / TOPIC / KNOWLEDGE）
     * @param contentId      内容主键ID
     * @param oldStatus      变更前状态，可为 null（首次创建时）
     * @param newStatus      变更后状态
     * @param operatorUserId 操作人员用户ID
     * @param reason         变更原因，可为 null
     */
    public void recordChange(
            String contentType,
            Long contentId,
            String oldStatus,
            String newStatus,
            Long operatorUserId,
            String reason
    ) {
        ContentStatusHistory history = new ContentStatusHistory();
        history.setContentType(contentType);
        history.setContentId(contentId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setOperatorUserId(operatorUserId);
        history.setReason(reason);

        mapper.insert(history);

        log.info("状态变更记录: type={}, id={}, {} -> {}, operator={}, reason={}",
                contentType, contentId, oldStatus, newStatus, operatorUserId, reason);
    }

    /**
     * 查询指定内容的状态变更历史，按时间倒序排列。
     *
     * @param contentType 内容类型
     * @param contentId   内容主键ID
     * @return 状态变更历史列表
     */
    public List<ContentStatusHistory> getHistory(String contentType, Long contentId) {
        LambdaQueryWrapper<ContentStatusHistory> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(ContentStatusHistory::getContentType, contentType)
                .eq(ContentStatusHistory::getContentId, contentId)
                .orderByDesc(ContentStatusHistory::getCreatedAt);

        return mapper.selectList(wrapper);
    }
}
