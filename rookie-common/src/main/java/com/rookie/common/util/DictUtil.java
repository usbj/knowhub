package com.rookie.common.util;

import cn.hutool.json.JSONUtil;
import com.rookie.common.cache.RedisCache;
import com.rookie.common.pojo.entity.SysDictData;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

//@Component
public class DictUtil {

    private static final String DICT_KEY = "sys_dict_name:";


    public static void setDictData(String name, List<SysDictData> dictData){
        SpringUtil.getBean(RedisCache.class).persistentSetCache(DICT_KEY+name,JSONUtil.toJsonStr(dictData));
    }

    public static List<SysDictData> getDictData(String name){
        return SpringUtil.getBean(RedisCache.class).getListCache(DICT_KEY+name, SysDictData.class);
    }

    public static void removeDictDataByDictKey(String name){
        SpringUtil.getBean(RedisCache.class).deleteCache(DICT_KEY+name);
    }

    public static void clearDictData(){
        Collection<String> keys = SpringUtil.getBean(RedisCache.class).keys(DICT_KEY + "*");
        SpringUtil.getBean(RedisCache.class).deleteCaches(keys);
    }

    public static String getValueByLabel(String name, String label){
        List<SysDictData> dictData = getDictData(name);
        SysDictData sysDictData = dictData.stream().filter(data -> data.getDictDataLabel().equals(label)).toList().get(0);
        if (sysDictData!=null){
            return sysDictData.getDictDataValue();
        }
        return null;
    }

}
