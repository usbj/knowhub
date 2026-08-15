package com.knowhub.controller.portal;

import com.knowhub.pojo.comment.vo.CommentCreateVo;
import com.knowhub.pojo.comment.vo.CommentReviewVo;
import com.knowhub.service.comment.impl.CommentService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台评论写接口（/authoring/comment/**，走 /authoring/** authenticated 兜底）。
 * <p>
 * 复用 blog 创作控制器范式：@PreAuthorize("isAuthenticated()") 作进接口门槛，service 层强判作者归属/自删/admin 短路。
 * 点赞 / 作者 inline 精选审核也在此 controller（作者无需走后台，inline 在评论区操作）。
 */
@Tag(name = "评论创作", description = "前台用户发评论/删评论/点赞/作者 inline 精选")
@RestController
@RequestMapping("/authoring/comment")
public class CommentAuthoringController {

    @Autowired
    CommentService commentService;

    @PostMapping
    @Operation(summary = "发评论（顶级或回复）")
    @Log(title = "评论发表", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Long> create(@RequestBody CommentCreateVo vo) {
        Long commentId = commentService.createComment(vo);
        return Result.success(commentId);
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "删评论（自删/作者删任意/admin 短路；删顶级连带回复）")
    @Log(title = "评论删除", businessType = BusinessType.DELETE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> delete(@PathVariable Long commentId) {
        Boolean b = commentService.deleteComment(commentId);
        return Result.success(b);
    }

    @PutMapping("/{commentId}/like")
    @Operation(summary = "点赞/取消点赞评论（liked=true 点赞, false 取消）")
    @Log(title = "评论点赞", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleLike(@PathVariable Long commentId,
                                      @RequestParam(required = false, defaultValue = "true") Boolean liked) {
        Boolean b = commentService.toggleLike(commentId, liked);
        return Result.success(b);
    }

    @PostMapping("/{commentId}/review")
    @Operation(summary = "作者 inline 精选（APPROVE 同意展示 / REJECT 拒绝）")
    @Log(title = "评论精选", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> review(@PathVariable Long commentId, @RequestBody CommentReviewVo vo) {
        Boolean b = commentService.reviewComment(commentId, vo);
        return Result.success(b);
    }
}