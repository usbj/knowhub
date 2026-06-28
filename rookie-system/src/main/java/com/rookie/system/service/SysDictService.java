package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.DictQuarry;
import com.rookie.system.pojo.vo.SysDictVO;

public interface SysDictService {

    PageInfo<SysDictVO> quarrySysDict(DictQuarry quarry);

    Boolean addSysDict(SysDictVO dictVo);

    Boolean editSysDictInfo(SysDictVO dictVo);

    Boolean deleteSysDictById(Long dictId);

    SysDictVO getSysDictById(Long dictId);

}
