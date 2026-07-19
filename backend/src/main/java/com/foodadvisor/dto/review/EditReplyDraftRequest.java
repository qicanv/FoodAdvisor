package com.foodadvisor.dto.review;

import lombok.Data;

/**
 * 编辑 AI 回复草稿的请求体（EPIC-02 故事7）
 *
 * 商家用户可以在 AI 生成内容的基础上进行修改，
 * 提交后 editedContent 会保存到草稿中，发布时以编辑后的内容为准。
 */
@Data
public class EditReplyDraftRequest {

    /**
     * 商家编辑后的回复内容。
     * 不能为空或仅包含空白字符。
     */
    private String editedContent;
}
