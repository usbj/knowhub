package com.knowhub.mapper;

import com.knowhub.pojo.entity.ProjectCompetition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 比赛项目子表 Mapper（1:1，主键兼外键）。
 * 仅 insert（新增项目时配套写）+ 按 projectId 查/改/删（随主表）。
 * PRACTICE/OPS 暂不做，后续加子表时照本 Mapper 结构新建。
 */
@Mapper
public interface ProjectCompetitionMapper {

    /** 按 projectId 查比赛特有字段（项目详情/编辑回显） */
    ProjectCompetition getByProjectId(Long projectId);

    /** 新增比赛子表行（projectId 为主键兼外键） */
    Boolean addProjectCompetition(ProjectCompetition pc);

    /** 编辑比赛子表行（按 projectId 主键） */
    Boolean editProjectCompetition(ProjectCompetition pc);

    /** 按 projectId 物理删（主表软删时配套清理子表行，子表不软删随主表） */
    Boolean deleteByProjectId(Long projectId);
}
