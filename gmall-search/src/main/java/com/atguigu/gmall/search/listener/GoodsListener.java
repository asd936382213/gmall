package com.atguigu.gmall.search.listener;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GoodsListener {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "SEARCH_SAVE_QUEUE",durable = "true"),
            exchange = @Exchange(value = "PMS_SPU_EXCHANGED",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"item.insert"}
    ))
    public void listener(Long spuId, Channel channel, Message message) throws IOException {
//查询spu下sku的集合
        ResponseVo<List<SkuEntity>> skuResponseVo = pmsClient.querySkusBySpuId(spuId);
        List<SkuEntity> skuEntities = skuResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuEntities)){
            List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                Goods goods = new Goods();
                //把sku相关信息设置进来
                goods.setSkuId(skuEntity.getId());
                goods.setPrice(skuEntity.getPrice().doubleValue());
                goods.setTitle(skuEntity.getTitle());
                goods.setSubTitle(skuEntity.getSubtitle());
                goods.setDefaultImage(skuEntity.getDefaultImage());

                //spu中的创建时间
                ResponseVo<SpuEntity> spuEntityResponseVo = pmsClient.querySpuById(spuId);
                SpuEntity spuEntity = spuEntityResponseVo.getData();
                if (spuEntity != null) {
                    goods.setCreateTime(spuEntity.getCreateTime());
                }

                //查询库存相关信息并set
                ResponseVo<List<WareSkuEntity>> wareResponseVo = wmsClient.queryWareSkuBySkuId(skuEntity.getId());
                List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                if (!CollectionUtils.isEmpty(wareSkuEntities)){
                    //只要有任何一个仓库的库存-锁定库存>0
                    goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));

                    goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a,b) -> a + b).get());
                }

                //查询品牌
                ResponseVo<BrandEntity> brandEntityResponseVo = pmsClient.queryBrandById(skuEntity.getBrandId());
                BrandEntity brandEntity = brandEntityResponseVo.getData();
                if (brandEntity != null){
                    goods.setBrandId(brandEntity.getId());
                    goods.setBrandName(brandEntity.getName());
                    goods.setLogo(brandEntity.getLogo());
                }
                //查询分类
                ResponseVo<CategoryEntity> categoryEntityResponseVo = pmsClient.queryCategoryById(skuEntity.getCatagoryId());
                CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                if (categoryEntity != null){
                    goods.setCategoryId(categoryEntity.getId());
                    goods.setCategoryName(categoryEntity.getName());
                }

                //查询规格参数
                List<SearchAttrValue> searchAttrValues = new ArrayList<>();
                //销售类型的检索规格参数及值
                ResponseVo<List<SkuAttrValueEntity>> skuAttrValueResponseVo = pmsClient.querySearchSkuAttrValuesByCidAndSkuId(skuEntity.getCatagoryId(), skuEntity.getId());
                List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrValueResponseVo.getData();
                if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                    searchAttrValues.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                        BeanUtils.copyProperties(skuAttrValueEntity,searchAttrValue);
                        return searchAttrValue;
                    }).collect(Collectors.toList()));
                }

                //基本类型的检索规格参数及值
                ResponseVo<List<SpuAttrValueEntity>> spuAttrValuesResponseVo = pmsClient.querySearchSpuAttrValuesByCidAndSpuId(skuEntity.getCatagoryId(), skuEntity.getSpuId());
                List<SpuAttrValueEntity> spuAttrValueEntities = spuAttrValuesResponseVo.getData();
                if (!CollectionUtils.isEmpty(spuAttrValueEntities)){
                    searchAttrValues.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                        SearchAttrValue searchAttrValue = new SearchAttrValue();
                        BeanUtils.copyProperties(spuAttrValueEntity,searchAttrValue);
                        return searchAttrValue;
                    }).collect(Collectors.toList()));
                }
                goods.setSearchAttrs(searchAttrValues);

                return goods;
            }).collect(Collectors.toList());

            goodsRepository.saveAll(goodsList);
        }

        //手动确认消息
        try {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (IOException e) {
            e.printStackTrace();
            //是否已经重试过
            if (message.getMessageProperties().getRedelivered()){
                //重试过
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,false);
            }else {
                //没有重试过，重新入队
                channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            }
        }
    }
}
