package com.rookie.system.mapper;


import com.rookie.common.pojo.entity.SysDictData;
import com.rookie.system.pojo.quarry.DictDataQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysDictDataMapper {

    List<SysDictData> quarrySysDictData(DictDataQuarry quarry);

    Boolean addSysDictData(SysDictData dictData);

    Boolean editSysDictDataInfo(SysDictData dictData);

    Boolean deleteSysDictDataByDataId(Long dictDataId);

    SysDictData getSysDictDataInfoByDataId(Long dictDataId);

    List<SysDictData> getSysDictDataByDictKey(String dictKey);


}
