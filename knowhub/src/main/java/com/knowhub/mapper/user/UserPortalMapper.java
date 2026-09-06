package com.knowhub.mapper.user;

import com.knowhub.pojo.user.vo.UserPortalVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户公开主页 Mapper（/portal/user/{userId}，只读 sys_user 单条）。
 * <p>
 * 不依赖 rookie-system 的 {@code SysUserMapper}，避免改 rookie；只读 sys_user 表不算违规
 * （同义于 {@link AuthoringUserMapper} 模式）。仅查 delete=0 AND status=1 的有效用户，
 * 输出列由 resultMap 限定为 userId/nickName/avatar/username/sex/createTime（不含 phoneNumber 等敏感字段）。
 *
 * @author knowhub
 */
@Mapper
public interface UserPortalMapper {

    /**
     * 按用户 ID 查公开主页信息：强制 delete=0 AND status=1 过滤已删/停用账号，
     * 返回 null 表示用户不存在或不可见。输出列由 resultMap 限定为公开字段集。
     */
    UserPortalVo getPublicUserById(Long userId);
}
