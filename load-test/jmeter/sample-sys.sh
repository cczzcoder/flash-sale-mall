#!/bin/sh
# 用法: sh load-test/jmeter/sample-sys.sh [输出csv] [采样秒数]
# 压测矩阵运行期间采集整机/进程 CPU 与可用内存（Windows typeperf，1s 间隔）
set -u
OUT="${1:-target/perftest/sys.csv}"
SECS="${2:-700}"
mkdir -p target/perftest
typeperf "\Processor(_Total)\% Processor Time" "\Memory\Available MBytes" "\Process(java)\% Processor Time" "\Process(mysqld)\% Processor Time" -si 1 -sc "$SECS" -f CSV -o "$OUT"
echo "written: $OUT"
