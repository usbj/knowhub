package com.knowhub.mapper;

import com.knowhub.pojo.entity.Project;
import com.knowhub.pojo.quarry.ProjectQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目主表 Mapper。
 * 列表查询带 author_nickname(join sys_user on author_id)；权限过滤透传 userViewLevel/userId。
 * 列表查询不带 description（大字段，详情接口单独查），避免拖列表。
 */
@Mapper
public interface ProjectMapper {

    /**
     * 列表查询（PageHelper 在 Service 层 startPage 拦截）。
     * 权限过滤：level <= userViewLevel OR project_id IN (member 子查询 where user_id=? and can_view=1)。
     * 返回行带 author_nickname（join sys_user），由 resultMap 映射。
     */
    List<Project> quarryProject(ProjectQuarry quarry);

    /** 详情：按主键取未删除项目（带 author_nickname + description 大字段） */
    Project getProjectInfoById(Long projectId);

    /**
     * 按 userId 查其可查看的项目 ID 集合（member.can_view=1 的项目）。
     * 用于无系统查看权限者（userViewLevel=0）的列表过滤兜底，以及前端"我参与的项目"。
     */
    List<Long> listViewableProjectIdsByUser(Long userId);

    /** 新增项目，回填主键 */
    Boolean addProject(Project project);

    /** 编辑项目（动态列） */
    Boolean editProjectInfo(Project project);

    /** 软删项目 */
    Boolean softDeleteProject(Long projectId);

    /** 对账用：查所有处于待审核且未删除的项目 ID（审核开关关闭后定时任务批量放行） */
    List<Long> listPendingReviewIds();
}
