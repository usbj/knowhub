package com.rookie.system.mapper;


import com.rookie.common.pojo.entity.SysUser;
import com.rookie.system.pojo.quarry.UserQuarry;
import com.rookie.system.pojo.vo.SysUserVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysUserMapper {

    List<SysUser> quarryUser(UserQuarry userQuarry);

    Boolean editUserInfo(SysUser user);

    SysUserVo selectSysUserById(Long userId);

    Boolean addSysUser(SysUser user);

    Boolean usernameIsExistOrNot(String username);

    Boolean phoneIsExistOrNot(String phoneNumber);

    Boolean deleteSysUserById(Long userId);

    Boolean changeSysUserStatus(Long userId,Integer status);

    SysUser getSysUserInfoById(Long userId);

    Boolean resetSysUserPassword(SysUser sysUser);

}
