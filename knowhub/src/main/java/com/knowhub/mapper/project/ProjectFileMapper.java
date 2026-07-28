package com.knowhub.mapper.project;

import com.knowhub.pojo.project.entity.ProjectFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目文件树 Mapper（支撑 GitHub 式侧边栏布局）。
 * 按 projectId 拉全树（扁平带 parentId，service 层组装为树）；叶子 join file_object 带出元数据。
 * 删项目时按 projectId 级联软删全部树节点。
 */
@Mapper
public interface ProjectFileMapper {

    /**
     * 按 projectId 查全部文件树节点（扁平，带 parentId）。
     * 叶子节点 join file_object 带出 originalName/contentLength/contentType/businessType。
     * service 层按 parentId 内存组装为树。
     */
    List<ProjectFile> listByProjectId(Long projectId);

    /** 按 fileId 查单条（编辑/删除前校验） */
    ProjectFile getFileById(Long fileId);

    /** 新增树节点（目录或文件叶子），回填主键 */
    Boolean addFile(ProjectFile file);

    /** 编辑树节点（动态列，改名/移动/排序） */
    Boolean editFile(ProjectFile file);

    /** 软删树节点 */
    Boolean softDeleteFile(Long fileId);

    /** 按 projectId 软删全部树节点（删项目时级联） */
    Boolean softDeleteByProjectId(Long projectId);
}
