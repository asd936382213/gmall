package com.atguigu.gmall.oms.mapper;

import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单项信息
 * 
 * @author wang
 * @email wang@atguigu.com
 * @date 2020-10-27 19:01:36
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {
	
}