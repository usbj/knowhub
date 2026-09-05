#!/bin/sh
# ===========================================================================
# knowhub MySQL 容器初始化脚本
# ---------------------------------------------------------------------------
# 挂到 /docker-entrypoint-initdb.d/，MySQL 官方镜像首启时自动执行。
#
# 数据来源：sql/knowhub.sql —— 由 mysqldump 导出的整库快照（含全部表结构 + 数据），
#   自带 DROP TABLE IF EXISTS / SET FOREIGN_KEY_CHECKS=0 / SET NAMES utf8mb4 等会话设置，
#   是自洽的单文件，直接灌即可。此前 37 个分脚本 + 拓扑排序的初始化方式已废弃。
#
# 为什么还要一个 shell 脚本而不直接把 knowhub.sql 放进 initdb.d 目录：
#   1. 显式 log 每个步骤，便于首启排障；
#   2. 文件名 00-init.sh 保证它在 initdb.d 目录里最先执行（若日后再加 .sql 也不抢序）；
#   3. 集中处理「库已存在 / 脚本缺失」等边界，失败即退出避免半残库。
# ===========================================================================
set -e

SQL_DIR="/sql"
DUMP_FILE="$SQL_DIR/knowhub.sql"

echo "[knowhub-init] 开始初始化 knowhub 数据库..."

if [ ! -f "$DUMP_FILE" ]; then
  echo "[knowhub-init] 错误：未找到整库快照 $DUMP_FILE，请把 mysqldump 导出的 knowhub.sql 放到 sql/ 目录"
  exit 1
fi

echo "[knowhub-init] 灌入整库快照 knowhub.sql（表结构 + 数据）"
# knowhub.sql 内已含 DROP TABLE IF EXISTS / FOREIGN_KEY_CHECKS=0 / NAMES utf8mb4，
# 直接 source 进 MYSQL_DATABASE（官方镜像首启时已建好该库）。
mysql -h localhost -u root -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "$DUMP_FILE"

echo "[knowhub-init] 数据库初始化完成。"
