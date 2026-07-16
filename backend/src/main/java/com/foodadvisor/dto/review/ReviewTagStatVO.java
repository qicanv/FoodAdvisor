//标签统计接口返回对象
package com.foodadvisor.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商家评价标签统计项。
 * 对应接口 GET /api/merchants/{merchantId}/review-tags 的返回数组元素。
 *
 * 示例 JSON：
 * {
 *   "tagCode": "TASTE_GOOD",
 *   "tagName": "口味好",
 *   "category": "TASTE",
 *   "positiveCount": 58,
 *   "neutralCount": 3,
 *   "negativeCount": 2,
 *   "totalCount": 63
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTagStatVO {

    /** 标签编码，如 TASTE_GOOD */
    private String tagCode;

    /** 标签中文名，如 "口味好" */
    private String tagName;

    /** 标签类别：TASTE / ENVIRONMENT / SERVICE / PRICE / QUEUE_TIME / PORTION / HYGIENE / SPEED / PARKING */
    private String category;

    /** 关联该标签且情感为 POSITIVE 的公开评价数量 */
    private Long positiveCount;

    /** 关联该标签且情感为 NEUTRAL 的公开评价数量 */
    private Long neutralCount;

    /** 关联该标签且情感为 NEGATIVE 的公开评价数量 */
    private Long negativeCount;

    /** 关联该标签的公开评价总数 = positiveCount + neutralCount + negativeCount */
    private Long totalCount;
}
