package com.foodadvisor.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 分页结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long pageNum;
    private long pageSize;
    private long total;
    private List<T> records;

    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords()
        );
    }
}
