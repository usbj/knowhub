package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.entity.SysDictData;
import com.rookie.system.pojo.quarry.DictDataQuarry;
import com.rookie.system.pojo.vo.SysDictDataVo;

import java.util.List;

public interface SysDictDataService {

    PageInfo<SysDictDataVo> quarrySysDictData(DictDataQuarry quarry);

    Boolean addSysDictData(SysDictDataVo dictDataVo);

    Boolean editSysDictDataInfo(SysDictDataVo dictDataVo);

    Boolean deleteSysDictDataByDataId(Long dictDataId);

    SysDictDataVo getSysDictDataByDataId(Long dictDataId);

    List<SysDictDataVo> getSysDictDataByDictKey(String dictKey);
}
