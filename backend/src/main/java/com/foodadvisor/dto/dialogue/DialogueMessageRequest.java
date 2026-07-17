package com.foodadvisor.dto.dialogue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DialogueMessageRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @Size(max = 1000, message = "content cannot exceed 1000 characters")
    private String content;

    @NotNull(message = "requestId is required")
    @Size(max = 100, message = "requestId cannot exceed 100 characters")
    private String requestId;

    @DecimalMin(value = "-90.0", message = "userLatitude cannot be less than -90")
    @DecimalMax(value = "90.0", message = "userLatitude cannot be greater than 90")
    private BigDecimal userLatitude;

    @DecimalMin(value = "-180.0", message = "userLongitude cannot be less than -180")
    @DecimalMax(value = "180.0", message = "userLongitude cannot be greater than 180")
    private BigDecimal userLongitude;

    @JsonIgnore
    public boolean isLocationPairValid() {
        return (userLatitude == null && userLongitude == null)
                || (userLatitude != null && userLongitude != null);
    }
}
