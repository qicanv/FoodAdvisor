package com.foodadvisor.dto;

import lombok.Data;
import java.time.LocalTime;

@Data
public class BusinessHourVO {

    /** 1=周一, 7=周日 */
    private Integer dayOfWeek;

    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean isClosed;
    private Boolean crossesMidnight;
}
