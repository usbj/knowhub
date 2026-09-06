package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.history.vo.ViewHistoryVo;
import com.knowhub.service.history.impl.ViewHistoryService;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户浏览历史接口。
 * 走 authenticated 兜底（/history 不进 /portal/ permitAll 区，见第一链路决策#10）。
 * 不加 @PreAuthorize：登录即可访问自己的历史，无权限键门槛。
 */
@Tag(name = "浏览历史", description = "当前用户浏览历史查看/删除/清空")
@RestController
@RequestMapping("/history")
public class HistoryController {

    @Autowired
    ViewHistoryService viewHistoryService;

    @GetMapping("/list")
    @Operation(summary = "当前用户浏览历史分页")
    public Result<PageInfo<ViewHistoryVo>> listHistory(@RequestParam(required = false) String bizType) {
        PageInfo<ViewHistoryVo> page = viewHistoryService.listHistory(bizType);
        return Result.success(page);
    }

    @DeleteMapping("/{viewId}")
    @Operation(summary = "删单条浏览历史")
    public Result<Boolean> deleteHistory(@PathVariable Long viewId) {
        Boolean b = viewHistoryService.deleteHistory(viewId);
        return Result.success(b);
    }

    @DeleteMapping("/clear")
    @Operation(summary = "清空当前用户全部浏览历史")
    public Result<Boolean> clearHistory() {
        Boolean b = viewHistoryService.clearHistory();
        return Result.success(b);
    }
}