package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.comment.vo.CommentPortalVo;
import com.knowhub.pojo.comment.vo.CommentReplyVo;
import com.knowhub.service.comment.impl.CommentPortalService;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台评论读接口（/portal/comment/**，走 /portal/** permitAll）。
 * <p>
 * 不加 @PreAuthorize：未登录也能看已「同意展示」的评论，登录态在 service 防御性取态拼权限谓词。
 * 写走 {@link CommentAuthoringController}（/authoring/comment/** authenticated）。
 */
@Tag(name = "前台评论", description = "博客/文章/项目/资源统一评论列表与回复")
@RestController
@RequestMapping("/portal/comment")
public class CommentPortalController {

    @Autowired
    CommentPortalService commentPortalService;

    @GetMapping("/list")
    @Operation(summary = "顶级评论分页（指定作品下）")
    public Result<PageInfo<CommentPortalVo>> listComments(@RequestParam String bizType,
                                                          @RequestParam Long bizId,
                                                          @RequestParam(required = false) String order) {
        PageInfo<CommentPortalVo> page = commentPortalService.listComments(bizType, bizId, order);
        return Result.success(page);
    }

    @GetMapping("/replies/{commentId}")
    @Operation(summary = "某顶级评论下的回复分页")
    public Result<PageInfo<CommentReplyVo>> listReplies(@PathVariable Long commentId) {
        PageInfo<CommentReplyVo> page = commentPortalService.listReplies(commentId);
        return Result.success(page);
    }
}