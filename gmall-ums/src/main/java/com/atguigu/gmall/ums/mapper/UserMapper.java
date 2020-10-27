package com.atguigu.gmall.ums.mapper;

import com.atguigu.gmall.ums.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表
 * 
 * @author wang
 * @email wang@atguigu.com
 * @date 2020-10-27 19:20:17
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
	
}
