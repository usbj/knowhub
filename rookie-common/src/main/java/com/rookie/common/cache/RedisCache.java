package com.rookie.common.cache;


import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
public class RedisCache {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String BASE_KEY;

    @Value("${redis.expire-time}")
    private String EXPIRE_TIME;

    public boolean setCache(String key, String value, long time, TimeUnit type) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        redisTemplate.opsForValue().set(integrity,value,time,type);
        return true;
    }

    public boolean setCacheToSetTime(String key, String value) {
        return setCache(key,value,Long.parseLong(EXPIRE_TIME),TimeUnit.MINUTES);
    }


    public boolean persistentSetCache(String key, String value) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        redisTemplate.opsForValue().set(integrity,value);
        return true;
    }

    public String getCacheJson(String key) {
        if (key.isEmpty()) {
            return null;
        }
        String integrity = BASE_KEY+key;
        return redisTemplate.opsForValue().get(integrity);
    }

    public <T> T getObjectCache(String key, Class<T> tClass) {
        return JSONUtil.toBean(getCacheJson(key), tClass);
    }

//    public UserInfo getUserInfoCache(String key) {
//        return getObjectCache(key, UserInfo.class);
//    }

    public boolean expire(String key,Long time,TimeUnit type) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        return redisTemplate.expire(integrity,time,type);
    }

    public boolean changeTimeToSetTime(String key) {
        return expire(key,Long.parseLong(EXPIRE_TIME),TimeUnit.MINUTES);
    }

    public boolean changeTimeInSeconds(String key, Long time) {
        return expire(key,time,TimeUnit.SECONDS);
    }

    public boolean deleteCache(String key) {
        if (key.isEmpty()) {
            return false;
        }
        String integrity = BASE_KEY+key;
        return redisTemplate.delete(integrity);
    }
}
