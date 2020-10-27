package com.atguigu.gmall.sms.mapper;

import com.atguigu.gmall.sms.entity.CouponEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券信息
 * 
 * @author wang
 * @email wang@atguigu.com
 * @date 2020-10-27 19:18:22
 */
@Mapper
public interface CouponMapper extends BaseMapper<CouponEntity> {
	
}
