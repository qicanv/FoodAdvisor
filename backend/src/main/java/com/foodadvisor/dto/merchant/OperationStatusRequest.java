package com.foodadvisor.dto.merchant;

import lombok.Data;

@Data
public class OperationStatusRequest {

    /** OPERATING / SUSPENDED / CLOSED_PERMANENTLY */
    private String operationStatus;

    private String reason;
}
