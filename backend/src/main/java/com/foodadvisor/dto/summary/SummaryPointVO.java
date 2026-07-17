package com.foodadvisor.dto.summary;

import lombok.Data;
import java.util.List;

/** 摘要要点（优点/不足/推荐菜） */
@Data
public class SummaryPointVO {
    private String name;
    private Integer mentionCount;
    private List<Long> reviewIds;
}