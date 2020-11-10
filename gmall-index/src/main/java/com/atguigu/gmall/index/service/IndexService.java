package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.api.GmallPmsApi;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "index:cates:";

    public List<CategoryEntity> queryLvl1Categories() {

        ResponseVo<List<CategoryEntity>> listResponseVo = pmsClient.queryCategory(0L);
        return listResponseVo.getData();
    }

    public List<CategoryEntity> queryLvl2CategoriesWithSubsByPid(Long pid) {
        //先查询缓存，有，直接返回
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (StringUtils.isNotBlank(json)){
            //命中了直接反序列化返回
            return JSON.parseArray(json,CategoryEntity.class);
        }
        //没有命中，执行业务远程调用获取数据，最后放入缓存
        ResponseVo<List<CategoryEntity>> listResponseVo = pmsClient.queryLvl2CatesWithSubByPid(pid);
        List<CategoryEntity> categoryEntities = listResponseVo.getData();

        if (CollectionUtils.isEmpty(categoryEntities)){
            //为了防止缓存穿透：数据即使为null也缓存，为了防止缓存过多，缓存时间设置的极短
            redisTemplate.opsForValue().set(KEY_PREFIX + pid,JSON.toJSONString(categoryEntities),1, TimeUnit.MINUTES);
        }else {
            //为了防止缓存的雪崩，可以给缓存时间添加随机值
            redisTemplate.opsForValue().set(KEY_PREFIX + pid,JSON.toJSONString(categoryEntities),2160 + new Random().nextInt(900),TimeUnit.HOURS);
        }

        return categoryEntities;
    }

    public void testLoc(){
        String uuid = UUID.randomUUID().toString();
        Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", uuid, 2, TimeUnit.MINUTES);
        if (!lock){
            testLoc();
        }else {
            //获取锁成功后，执行业务逻辑
            String numString = redisTemplate.opsForValue().get("num");
            if (StringUtils.isEmpty(numString)){
                return;
            }
            int num = Integer.parseInt(numString);
            redisTemplate.opsForValue().set("num",String.valueOf(++num));

            //释放锁。为了防止误删，删除之前需要判断是不是自己的锁
            if (StringUtils.equals(uuid,redisTemplate.opsForValue().get("lock"))){
                redisTemplate.delete("lock");
            }
        }

    }
}
