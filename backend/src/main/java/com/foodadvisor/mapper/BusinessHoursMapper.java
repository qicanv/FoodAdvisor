package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.MerchantBusinessHours;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BusinessHoursMapper extends BaseMapper<MerchantBusinessHours> {

    List<MerchantBusinessHours> selectByMerchantId(Long merchantId);

    List<MerchantBusinessHours> selectByMerchantIds(
            List<Long> merchantIds
    );
}
