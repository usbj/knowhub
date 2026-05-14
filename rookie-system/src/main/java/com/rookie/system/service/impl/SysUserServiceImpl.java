package com.rookie.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.github.pagehelper.PageInfo;
import com.rookie.common.enums.ResultEnum;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysRole;
import com.rookie.common.pojo.entity.SysUser;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysRoleMapper;
import com.rookie.system.mapper.SysUserMapper;
import com.rookie.system.mapper.SysUserRoleMapper;
import com.rookie.system.pojo.SysUserRole;
import com.rookie.system.pojo.quarry.UserQuarry;
import com.rookie.system.pojo.vo.SysUserVo;
import com.rookie.system.service.SysUserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SysUserServiceImpl implements SysUserService {

    @Autowired
    SysUserMapper sysUserMapper;

    @Autowired
    SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    SysRoleMapper sysRoleMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    private SysRole DEFAULT_ROLE;


    @Override
    public PageInfo<SysUserVo> quarrySysUser(UserQuarry userQuarry) {
        //启动分页工具
        PageUtil.startPage();
        //获取用户数据并转化为分页的VO数据返回
        List<SysUser> userEntities = sysUserMapper.quarryUser(userQuarry);
        PageInfo<SysUser> sysUserPageInfo = PageUtil.packagedPageInfo(userEntities);
        return PageUtil.copyPageInfo(sysUserPageInfo, SysUserVo.class);
    }

    @Override
    public SysUserVo selectSysUserVoById(Long userId) {
        return sysUserMapper.selectSysUserById(userId);
    }

    @Override
    @Transactional
    public Boolean editSysUserInfo(SysUserVo userVo) {
        //清楚用户原先的角色
        sysUserRoleMapper.deleteUserRoleInfo(userVo.getUserId());
        //批量增加用户被分配的角色
        addRolesInBulk(userVo);

        //将vo转换为entity实体
        SysUser sysUser = BeanUtil.toBean(userVo, SysUser.class);
        UserInfo updateBy = (UserInfo)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysUser.setUpdateBy(updateBy.getNickName());

        return sysUserMapper.editUserInfo(sysUser);
    }



    @Override
    @Transactional
    public Boolean addSysUserInfo(SysUserVo sysUserVo) {
        DEFAULT_ROLE=sysRoleMapper.getDefaultRole();

        if (accountIsOnlyOrNot(sysUserVo)) {
            throw new ServiceException(ResultEnum.INCREASE_ERROR);
        }

        //将vo转化为entity
        SysUser sysUser = BeanUtil.toBean(sysUserVo, SysUser.class);
        //加密密码
        encryptPasswords(sysUser);
        //获取创建者
        UserInfo creator = (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        sysUser.setCreateBy(creator.getUsername());
        sysUser.setUpdateBy(creator.getUsername());
        //存入用户
        sysUserMapper.addSysUser(sysUser);
        //设置用户id
        sysUserVo.setUserId(sysUser.getUserId());
        //判断新用户是否有角色信息
        roleIsNUll(sysUserVo);
        //添加用户的角色信息
        addRolesInBulk(sysUserVo);
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteSysUser(Long[] userId) {
        for (Long l : userId) {
            sysUserMapper.deleteSysUserById(l);
            sysUserRoleMapper.deleteUserRoleInfo(l);
        }
        return true;
    }

    @Override
    public Boolean chargeSysUserStatus(Long userId, Integer status) {
        sysUserMapper.changeSysUserStatus(userId,status);
        return true;
    }

    @Override
    public Boolean modifyPersonalDetails(SysUserVo sysUserVo) {
        SysUser sysUser = BeanUtil.toBean(sysUserVo, SysUser.class);
        try {
            sysUserMapper.editUserInfo(sysUser);
        } catch (Exception e) {
            throw new ServiceException(500,"用户信息更改失败");
        }
        return true;
    }

    @Override
    public SysUserVo getPersonalDetails(Long userId) {
        SysUser sysUser = sysUserMapper.getSysUserInfoById(userId);
        SysUserVo userVo = BeanUtil.toBean(sysUser, SysUserVo.class);
        return userVo;
    }

    @Override
    public Boolean resetSysUserPassword(Long userId, String password) {
        SysUser sysUser = new SysUser();
        sysUser.setPassword(password);
        sysUser.setUserId(userId);
        encryptPasswords(sysUser);
        try {
            sysUserMapper.resetSysUserPassword(sysUser);
        } catch (Exception e) {
            throw new ServiceException(500,"密码重置失败");
        }
        return true;
    }

    /**
    * 批量添加用户的角色
    * */
    private void addRolesInBulk(SysUserVo userVo) {
        if (userVo.getRoleId().isEmpty()) {
            userVo.setRoleId(userVo.getUserRole().stream().map(SysRole::getRoleId).toList());
        }
        List<Long> roleId = userVo.getRoleId();
        ArrayList<SysUserRole> sysUserRoles = new ArrayList<>();
        for (Long id : roleId) {
            sysUserRoles.add(new SysUserRole(userVo.getUserId(),id));
        }
        try {
            sysUserRoleMapper.addUserRoleInfo(sysUserRoles);
        } catch (Exception e) {
            throw new ServiceException(500,"用户角色信息添加失败");
        }
    }

    /**
    * 加密密码
    * */
    private void encryptPasswords(SysUser sysUser) {
        if (StringUtils.isEmpty(sysUser.getPassword())) {
            sysUser.setPassword("123456");
        }
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
    }

    /**
     * 判断新增用户角色是否为空，是则添加默认角色
     * */
    private void roleIsNUll(SysUserVo sysUserVo){
        if(CollUtil.isEmpty(sysUserVo.getUserRole())||CollUtil.isEmpty(sysUserVo.getRoleId())){
            sysUserVo.setRoleId(new ArrayList<>());
            sysUserVo.setUserRole(new ArrayList<>());
            sysUserVo.getRoleId().add(DEFAULT_ROLE.getRoleId());
            sysUserVo.getUserRole().add(DEFAULT_ROLE);
        }
    }


    /**
     * 判断用户是否重复
    * */
    private boolean accountIsOnlyOrNot(SysUserVo sysUserVo) {
        if (sysUserMapper.usernameIsExistOrNot(sysUserVo.getUsername())!=null){
            return true;
        }
        if (sysUserMapper.phoneIsExistOrNot(sysUserVo.getPhoneNumber())!=null){
            return true;
        }
        return false;
    }

}
