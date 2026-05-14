package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.cache.RedisCache;
import com.rookie.common.pojo.entity.SysDictData;
import com.rookie.common.util.DictUtil;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysDictDataMapper;
import com.rookie.system.pojo.quarry.DictDataQuarry;
import com.rookie.system.pojo.vo.SysDictDataVo;
import com.rookie.system.service.SysDictDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class SysDictDataServiceImpl implements SysDictDataService {

    @Autowired
    SysDictDataMapper sysDictDataMapper;

    @Autowired
    RedisCache redisCache;

    @Override
    public PageInfo<SysDictDataVo> quarrySysDictData(DictDataQuarry quarry) {
        PageUtil.startPage();
        List<SysDictData> sysDictData = sysDictDataMapper.quarrySysDictData(quarry);
        PageInfo<SysDictData> sysDictDataPageInfo = PageUtil.packagedPageInfo(sysDictData);
        return PageUtil.copyPageInfo(sysDictDataPageInfo,SysDictDataVo.class);
    }

    @Override
    public Boolean addSysDictData(SysDictDataVo dictDataVo) {
        SysDictData dictDataEntity = BeanUtil.toBean(dictDataVo, SysDictData.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        dictDataEntity.setCreateBy(userInfo.getUsername());
        dictDataEntity.setUpdateBy(userInfo.getUsername());
        return sysDictDataMapper.addSysDictData(dictDataEntity);
    }

    @Override
    public Boolean editSysDictDataInfo(SysDictDataVo dictDataVo) {
        SysDictData dictDataEntity = BeanUtil.toBean(dictDataVo, SysDictData.class);
        UserInfo userInfo = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        dictDataEntity.setCreateBy(userInfo.getUsername());
        dictDataEntity.setUpdateBy(userInfo.getUsername());
        Boolean b = sysDictDataMapper.editSysDictDataInfo(dictDataEntity);
        if (b){
            List<SysDictData> dictDataList = sysDictDataMapper.getSysDictDataByDictKey(dictDataVo.getDictKey());
            DictUtil.setDictData(dictDataVo.getDictKey(),dictDataList);
        }
        return b;
    }

    @Override
    public Boolean deleteSysDictDataByDataId(Long dictDataId) {
        return sysDictDataMapper.deleteSysDictDataByDataId(dictDataId);
    }

    @Override
    public SysDictDataVo getSysDictDataByDataId(Long dictDataId) {
        SysDictData sysDictData = sysDictDataMapper.getSysDictDataInfoByDataId(dictDataId);
        SysDictDataVo sysDictDataVo = BeanUtil.toBean(sysDictData, SysDictDataVo.class);
        return sysDictDataVo;
    }

    @Override
    public List<SysDictDataVo> getSysDictDataByDictKey(String dictKey) {
        List<SysDictData> listCacheEntity = DictUtil.getDictData(dictKey);
        List<SysDictDataVo> listCache = BeanUtil.copyToList(listCacheEntity, SysDictDataVo.class);
        if (!listCache.isEmpty()){
            return listCache;
        }
        List<SysDictData> sysDictDataByDictKey = sysDictDataMapper.getSysDictDataByDictKey(dictKey);
        DictUtil.setDictData(dictKey,sysDictDataByDictKey);
        listCache = BeanUtil.copyToList(sysDictDataByDictKey,SysDictDataVo.class);
        return listCache;
    }
}
