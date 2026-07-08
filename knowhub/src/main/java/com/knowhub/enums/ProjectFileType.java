package com.knowhub.enums;

/**
 * 项目文件树节点类型，对应 project_file.is_dir 列。
 * DIRECTORY 目录（object_id=null，仅作层级骨架）/ FILE 文件（object_id 指向 file_object）。
 * 与 file_object 分工：file_object=对象存储元数据(扁平)，project_file=项目内目录树骨架。
 */
public enum ProjectFileType {

    DIRECTORY(1, "目录"),
    FILE(0, "文件");

    private final int code;

    private final String desc;

    ProjectFileType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static boolean isDir(Integer isDir) {
        return isDir != null && isDir == DIRECTORY.code;
    }
}
