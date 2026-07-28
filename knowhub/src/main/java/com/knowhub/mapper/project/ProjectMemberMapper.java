package com.knowhub.mapper.project;

import com.knowhub.pojo.project.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目成员 Mapper（团队名单 + 项目内权限标志位）。
 * 成员列表 join sys_user 带出 nickname/username；LEADER 唯一性由 service 层事务校验。
 */
@Mapper
public interface ProjectMemberMapper {

    /** 按 projectId 查成员列表（join sys_user 带出 nickname/username，按 member_role+member_id 排序） */
    List<ProjectMember> listByProjectId(Long projectId);

    /** 按 memberId 查单条（编辑/删除前校验） */
    ProjectMember getMemberById(Long memberId);

    /** 按 projectId+userId 查成员（判定当前用户是否成员/权限态） */
    ProjectMember getMember(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /** 按 projectId 查 LEADER（换负责人/唯一性校验用） */
    ProjectMember getLeader(Long projectId);

    /** 新增成员，回填主键 */
    Boolean addMember(ProjectMember member);

    /** 编辑成员（动态列，调整角色/标志位） */
    Boolean editMember(ProjectMember member);

    /** 软删成员 */
    Boolean softDeleteMember(Long memberId);

    /** 按 projectId 软删全部成员（删项目时级联） */
    Boolean softDeleteByProjectId(Long projectId);
}
