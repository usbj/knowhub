CREATE TABLE `dict` (
	`dict_id` BIGINT NOT NULL AUTO_INCREMENT UNIQUE COMMENT '字典主键',
	`dict_name` VARCHAR(255) NOT NULL COMMENT '字典名称',
	`dict_type` VARCHAR(255) NOT NULL COMMENT '字段备注',
	`status` CHAR(1) NOT NULL DEFAULT '1' COMMENT '字典状态',
	`remake` VARCHAR(255),
	`create_time` DATETIME NOT NULL COMMENT '创建时间',
	`create_by` VARCHAR(255) NOT NULL,
	`update_time` DATETIME NOT NULL,
	`update_by` VARCHAR(255) NOT NULL,
	PRIMARY KEY(`dict_id`)
);


CREATE TABLE `dict_data` (
	`dict_id` INTEGER NOT NULL COMMENT '数据所属表格',
	`dict_name` VARCHAR(255) NOT NULL COMMENT '所属字典名称',
	`dict_data_name` VARCHAR(255) NOT NULL COMMENT '数据名称标签',
	`dict_data_value` VARCHAR(255) NOT NULL COMMENT '数据真值',
	`remark` VARCHAR(255) COMMENT '备注',
	`dict_data_sort` TINYINT NOT NULL DEFAULT 1 COMMENT '数据排序',
	`create_time` DATETIME NOT NULL,
	`create_by` VARCHAR(255) NOT NULL,
	`update_time` DATETIME NOT NULL,
	`update_by` VARCHAR(255) NOT NULL
);


