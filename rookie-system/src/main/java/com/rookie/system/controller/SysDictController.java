package com.rookie.system.controller;


import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.DictQuarry;
import com.rookie.system.pojo.vo.SysDictVO;
import com.rookie.system.service.SysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
* TODO : 字典类型的增、删、查、改（改名字时要）、前端获取（缓存设置）、刷新缓存、详细信息
* */

@RestController
@RequestMapping("/sys/dict")
public class SysDictController {

    @Autowired
    SysDictService sysDictService;

    @GetMapping("/list")
    public Result<PageInfo<SysDictVO>> quarrySysDict(DictQuarry quarry){
        PageInfo<SysDictVO> sysDictVOPageInfo = sysDictService.quarrySysDict(quarry);
        return Result.success(sysDictVOPageInfo);
    }

    @PostMapping()
    @Log(title = "字典管理", businessType = BusinessType.INSERT)
    public Result<Boolean> addSysDict(@RequestBody SysDictVO dictVo){
        Boolean b = sysDictService.addSysDict(dictVo);
        return Result.success(b);
    }

    @PutMapping()
    @Log(title = "字典管理", businessType = BusinessType.UPDATE)
    public Result<Boolean> editSysDict(@RequestBody SysDictVO dictVo){
        Boolean b = sysDictService.editSysDictInfo(dictVo);
        return Result.success(b);
    }

    @DeleteMapping("/{dictId}")
    @Log(title = "字典管理", businessType = BusinessType.DELETE)
    public Result<Boolean> deleteSysDictById(@PathVariable Long dictId){
        Boolean b = sysDictService.deleteSysDictById(dictId);
        return Result.success(b);
    }

    @GetMapping("/{dictId}")
    public Result<SysDictVO> getSysDictInfo(@PathVariable Long dictId){
        SysDictVO sysDictById = sysDictService.getSysDictById(dictId);
        return Result.success(sysDictById);
    }


}
