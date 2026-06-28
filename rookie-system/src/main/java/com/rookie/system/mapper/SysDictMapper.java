package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysDict;
import com.rookie.system.pojo.quarry.DictQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysDictMapper {

    List<SysDict> quarrySysDict(DictQuarry quarry);

    Boolean addSysDict(SysDict dict);

    Boolean editSysDictInfo(SysDict dict);

    Boolean deleteSysDictById(Long dictId);

    SysDict getSysDictInfoById(Long dictId);
}
