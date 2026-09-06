package com.knowhub.mapper.project;

import com.knowhub.pojo.project.entity.ProjectReviewLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目审核流水 Mapper。
 * 仅 insert（追加）+ 按 project_id 查历史（升序，供前台时间线/后台审核记录展示）。
 * 流水表不改不删，故无 update/delete 方法。结构与 ResourceReviewLogMapper 同构。
 * action/role 复用 ReviewAction 枚举 + review_action 字典（博客/资源/项目共用）。
 */
@Mapper
public interface ProjectReviewLogMapper {

    /** 追加一条审核流水（动作时间由 DB 默认 CURRENT_TIMESTAMP 填充） */
    Boolean insertReviewLog(ProjectReviewLog log);

    /** 按项目ID查审核历史（按动作时间升序，还原轨迹） */
    List<ProjectReviewLog> listByProjectId(Long projectId);
}
