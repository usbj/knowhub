package com.rookie.system.controller;


import com.github.pagehelper.PageInfo;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.system.pojo.quarry.DictDataQuarry;
import com.rookie.system.pojo.vo.SysDictDataVo;
import com.rookie.system.service.SysDictDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * TODO :说明mysql和redis缓存一致性问题
 * */

@RestController
@RequestMapping("/sys/dist/data")
public class SysDictDataController {

    @Autowired
    SysDictDataService sysDictDataService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:dictData:quarry')")
    public Result<PageInfo<SysDictDataVo>> quarrySysDictData(DictDataQuarry quarry){
        PageInfo<SysDictDataVo> sysDictDataVoPageInfo = sysDictDataService.quarrySysDictData(quarry);
        return Result.success(sysDictDataVoPageInfo);
    }

    @PostMapping()
    @Log(title = "字典数据", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('system:dictData:add')")
    public Result<Boolean> addSysDictData(@RequestBody SysDictDataVo dictDataVo){
        Boolean b = sysDictDataService.addSysDictData(dictDataVo);
        return Result.success(b);
    }

    @PutMapping()
    @Log(title = "字典数据", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('system:dictData:edit')")
    public Result<Boolean> editSysDictData(@RequestBody SysDictDataVo dictDataVo){
        Boolean b = sysDictDataService.editSysDictDataInfo(dictDataVo);
        return Result.success(b);
    }

    @DeleteMapping("/{dictDataId}")
    @Log(title = "字典数据", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('system:dictData:delete')")
    public Result<Boolean> deleteSysDictDataByDataId(@PathVariable Long dictDataId){
        Boolean b = sysDictDataService.deleteSysDictDataByDataId(dictDataId);
        return Result.success(b);
    }

    @GetMapping("/{dictDataId}")
    @PreAuthorize("hasAuthority('system:dictData:info')")
    public Result<SysDictDataVo> getSysDictDataINfo(@PathVariable Long dictDataId){
        SysDictDataVo sysDictDataByDataId = sysDictDataService.getSysDictDataByDataId(dictDataId);
        return Result.success(sysDictDataByDataId);
    }

    @GetMapping("/type/{dictKey}")
    public Result<List<SysDictDataVo>> getSysDictDataByDictKey(@PathVariable String dictKey){
        List<SysDictDataVo> sysDictDataByDictKey = sysDictDataService.getSysDictDataByDictKey(dictKey);
        return Result.success(sysDictDataByDictKey);
    }

}
