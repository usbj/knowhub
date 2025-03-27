package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface SysRoleMapper {

    SysRole getDefaultRole();

}
