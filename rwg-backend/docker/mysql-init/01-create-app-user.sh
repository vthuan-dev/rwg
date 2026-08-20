#!/bin/bash
# =============================================================================
# Init script MySQL (chạy 1 lần khi volume khởi tạo lần đầu, bởi entrypoint
# /docker-entrypoint-initdb.d). Tạo user RIÊNG cho app (rwg_app) để app
# KHÔNG chạy bằng root (hardening theo code review).
# Biến môi trường có sẵn: MYSQL_ROOT_PASSWORD, MYSQL_DATABASE, DB_PASSWORD.
# =============================================================================
set -e

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<-EOSQL
    CREATE USER IF NOT EXISTS 'rwg_app'@'%' IDENTIFIED BY '${DB_PASSWORD}';
    GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
        ON \`${MYSQL_DATABASE}\`.* TO 'rwg_app'@'%';
    FLUSH PRIVILEGES;
EOSQL

echo "[rwg-init] Đã tạo user MySQL 'rwg_app' cho database '${MYSQL_DATABASE}'."
