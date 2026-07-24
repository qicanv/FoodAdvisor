package com.foodadvisor.dto.alert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectSensitiveRequest {

    /** 分析起始时间，默认为24小时前 */
    private OffsetDateTime startTime;

    /** 分析结束时间，默认为当前时间 */
    private OffsetDateTime endTime;

    /** 触发阈值：同一商家同一话题的最小评价数，默认为3 */
    private Integer threshold;

    /** 指定商家ID（可选），不指定则分析所有商家 */
    private Long merchantId;
}
