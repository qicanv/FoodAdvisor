package com.foodadvisor.dto.alert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAlertStatusRequest {

    /** 目标状态：PROCESSING / RESOLVED / DISMISSED */
    private String status;

    /** 处理备注 */
    private String remark;
}
