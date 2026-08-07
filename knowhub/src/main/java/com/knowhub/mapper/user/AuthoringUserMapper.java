package com.knowhub.mapper.user;

import com.knowhub.pojo.user.quarry.AuthoringUserSearchQuarry;
import com.knowhub.pojo.user.vo.AuthoringUserVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 前台选人 Mapper（/authoring/user/search，只读 sys_user）。
 * <p>
 * 不依赖 rookie-system 的 {@code SysUserMapper}，避免改 rookie；只读 sys_user 表不算违规
 * （同义于 user_view_history join sys_user 模式）。仅查 delete=0 AND status=1 的有效用户，
 * 不输出敏感字段（输出列由 resultMap 限定为 userId/nickName/username/avatar/phoneNumber/createTime）。
 *
 * @author knowhub
 */
@Mapper
public interface AuthoringUserMapper {

    /**
     * 模糊匹配 sys_user：nickName/username/phoneNumber 任一非空按该字段 LIKE，皆空走"最近注册兜底"分页。
     * SQL 内强制 delete=0 AND status=1 过滤掉已删/停用账号。分页由 PageHelper 在 Service 层接管。
     */
    List<AuthoringUserVo> searchAuthoringUsers(AuthoringUserSearchQuarry quarry);
}