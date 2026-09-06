-- 项目收藏事实表（对齐 blog_collect/article_collect/resource_collect 范式）。
-- 配套主表 project.collect_count 列 + ProjectMapper.incrCollectCount 已预留，本期前台互动落地复用。
-- 由后端"完善个人中心"需求新增（2026-08-11），仓库无统一 sql 脚本，需手动执行。
create table project_collect (
  project_id bigint       not null comment '项目 ID',
  user_id    bigint       not null comment '收藏者用户 ID',
  create_time datetime    default current_timestamp comment '收藏时间，作倒序排序',
  primary key (project_id, user_id),
  key idx_user_create (user_id, create_time)
) engine=innodb default charset=utf8mb4 comment='项目收藏事实表';