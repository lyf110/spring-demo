package cn.lyf.redis.controller;

import cn.lyf.redis.anno.RequestLimit;
import cn.lyf.redis.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * @author lyf
 * @version 1.0
 * @classname GoodsController
 * @description
 * @since 2023/2/28 14:43
 */
@Slf4j
@RestController
@RequestMapping(value = "/goods")
public class GoodsController {

    private static final String GOODS_LIMIT_ZSET = "goods:limit:zset";
    private static final String GOODS_LIMIT_LIST = "goods:limit:list";
    @Autowired
    @Qualifier(value = "jsonRedisTemplate")
    private RedisTemplate<String, Serializable> redisTemplate;

    @GetMapping(value = "/test")
    @RequestLimit
    public Result<String> testLimit() {
        return Result.ok("请求成功");
    }


    @RequestMapping(value = "/findAll")
    public String limitFlow() {
        long currentTime = System.currentTimeMillis();
        log.info("currentTime: {}", currentTime);
        long intervalTime = 10 * 1000L;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(GOODS_LIMIT_ZSET))) {
            int limitCount = Objects.requireNonNull(redisTemplate.opsForZSet().rangeByScore(GOODS_LIMIT_ZSET, currentTime - intervalTime, currentTime)).size();
            if (limitCount > 5) {
                return "10秒只能访问5次";
            }
        }

        redisTemplate.opsForZSet().add(GOODS_LIMIT_ZSET, UUID.randomUUID().toString(), currentTime);
        return getGoods();
    }

    @RequestMapping(value = "/findAll2")
    public String limitFlow2() {
        Serializable result = redisTemplate.opsForList().leftPop(GOODS_LIMIT_LIST);
        if (result == null) {
            return "当前令牌桶中没有令牌";
        }

        return getGoods();
    }

    /**
     * 1秒的速率往令牌桶中添加UUID，只为保证唯一性
     */
    @Scheduled(fixedDelay = 1_000, initialDelay = 0)
    public void setIntervalTimeTask() {
        int capacity = 60;
        if (capacity > redisTemplate.boundListOps(GOODS_LIMIT_LIST).size()) {
            redisTemplate.boundListOps(GOODS_LIMIT_LIST).rightPush(UUID.randomUUID().toString());
        }
    }

    public String getGoods() {
        return "商品获取成功";
    }
}
