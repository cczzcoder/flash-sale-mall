# 《秒杀商城 v1.0.0 压测报告》

- 实测时间：2026-10-07 16:34:06 – 16:42（CST），7 个场景连续跑完
- 被测版本：main @ 474c714（本地提交，未推送），fat jar 运行于 localhost:8080
- 口径：QPS = 样本数 ÷（末次响应完成时间 − 首个请求发出时间）；分位为 JTL 全量样本排序值。本报告全部为实测值，不照抄任何模板假设。

## 1. 测试环境

| 项 | 模板假设 | 本机实测 |
|---|---|---|
| 压测工具 | JMeter 5.5 | **JMeter 5.6.3** |
| 机器 | 本地 8核 16G | Windows 本机，8 物理核 / 16 逻辑核，15.2 GB 内存 |
| JDK | — | Java 21.0.8 |
| MySQL | 8.0 | 8.0.43（Windows 服务 MySQL80，127.0.0.1:3306，库 seckill） |
| Redis | 6.0 | **7.4.11**（Docker 容器 seckill-redis，127.0.0.1:16379） |
| RabbitMQ | — | 3.13（容器 seckill-rabbitmq；本次只读场景未涉及） |
| 应用 | — | Spring Boot fat jar；Tomcat 默认 200 工作线程；Jedis 池 maxTotal=1000 / maxIdle=500 / minIdle=100 / maxWait=500ms |
| 部署拓扑 | — | ⚠️ **单机压测**：JMeter、应用、MySQL、Redis 跑在同一台机器（见 3.2 的解读限制） |
| 数据规模 | — | goods 7 行 / seckill_goods 7 行（本地演示数据） |
| 压测方法 | — | 每场景定长 60s、ramp 5s、无限循环；只读 GET、keepalive、HttpClient4；/user/info 用真实登录 token（每轮运行前重新登录取新值） |

场景范围说明：

- 被测接口：`GET /goods/list`（Redis 60s HTML 缓存 + SETNX 单飞重建）、`GET /goods/listWithoutCache`（每次查库 + Thymeleaf 渲染，对照）、`GET /user/info`（每请求含 Redis 读 token + MySQL 回源 + Set-Cookie 续期）。
- `GET /goods/detailStatic/{id}` 未纳入矩阵：该接口有 `@AccessLimit(1s/10/IP)`，超限返回 500104 属预期保护行为，不构成 QPS 目标。
- 全程 0 个 HTTP 错误、0 个业务错误码；应用控制台日志在压测窗口内 0 条 ERROR。

## 2. 核心指标结果

| 场景 | 并发 | 样本数 | QPS | 错误率 | Avg | P50 | P90 | P95 | P99 | Max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| GET /goods/list（缓存） | 50 | 179,632 | **3,001** | 0% | 15.9ms | 14 | 25 | 31 | **46ms** | 247ms |
| GET /goods/list（缓存） | 100 | 157,018 | **2,622** | 0% | 36.3ms | 32 | 62 | 73 | **103ms** | 330ms |
| GET /goods/list（缓存） | 200 | 153,043 | **2,554** | 0% | 74.9ms | 70 | 117 | 136 | **198ms** | 859ms |
| GET /goods/listWithoutCache（对照） | 50 | 131,423 | 2,196 | 0% | 21.8ms | 12 | 52 | 69 | 119ms | 741ms |
| GET /goods/listWithoutCache（对照） | 100 | 162,493 | 2,713 | 0% | 35.3ms | 10 | 99 | 121 | 183ms | 553ms |
| GET /user/info | 50 | 150,308 | **2,510** | 0% | 19.1ms | 18 | 25 | 28 | **36ms** | 104ms |
| GET /user/info | 100 | 143,000 | **2,388** | 0% | 40.1ms | 39 | 55 | 62 | **77ms** | 187ms |

主要读数：

- 商品列表（缓存路径）50 并发 **3,001 QPS / P99 46ms**；100 并发 2,622 QPS；200 并发 2,554 QPS。50→200 并发 QPS 进入 ~2.5k–3k 平台，延迟近似线性上升（16→36→75ms avg），**0 错误、0 超时**，属资源饱和型上限，未出现雪崩。
- 缓存 vs 非缓存对照：50 并发下缓存路径 QPS 高约 37%（3,001 vs 2,196）、P99 低约 61%（46 vs 119ms）；100 并发下两者收敛（2,622 vs 2,713，同一量级）——CPU 饱和后两条路径都被 CPU 限住。
- 用户中心 50 并发 **2,510 QPS / P99 36ms**，100 并发 2,388 QPS / P99 77ms；单请求成本（Redis + MySQL 主键查询 + Cookie 续期）在该量级下不成瓶颈。

## 3. 瓶颈与优化

### 3.1 模板假设核查（两条均不成立，以实测为准）

- **"分类过滤字段未加复合索引，导致全表扫描"——不成立。** 商品分类过滤是列表页前端 JS 行为（goods_list.html 的分类 pills 按 data-cat 过滤已渲染的 DOM），后端 SQL（`GoodsDao.getGoodsVoList`）为 `left join` 且没有任何 WHERE/分类条件，压测流量完全不触碰分类字段；且 goods 表当前仅 7 行。加 `idx_category_status` 对本项目的读路径没有任何收益。
- **"200 并发时 QPS 跌破 500"——不成立。** 实测 200 并发 2,554 QPS / P99 198ms / 0 错误。真实曲线是从 50 并发起进入 ~2.5k–3k 平台，而不是崩塌。

### 3.2 实测瓶颈

- **唯一可复现的瓶颈是"单机资源"**：JMeter（压测端）与被测应用、MySQL、Redis 共享同一台 8C/16T 机器，JMeter 自身要吃掉大量 CPU 来生成 ~2.6k QPS 流量并下载 22.5KB/响应。三种只读接口在 100→200 并发时全部收敛到同一量级（2.4k–2.7k），延迟随并发线性上升而无错误——典型 CPU 饱和特征。
  要得到被测系统的真实上限，需要把 JMeter 拆到独立机器（或独立进程组限核），本报告的绝对值是**下限**。
- **带宽次要**：列表页响应体 22.5KB，峰值约 56–66 MB/s（loopback）。当前未开启 gzip（`server.compression`），对真实网络用户可评估开启，对本机压测无影响。
- **/user/info 的回源设计是未来先承压点，但当前不是瓶颈**：`getByToken` 每请求做一次 MySQL 回源（保证信息新鲜度）+ Set-Cookie 续期；实测 2.4k–2.5k QPS 下 MySQL 主键查询毫无压力。若后续并发更高或 DB 分离部署，可选优化：给该回源加 1–2s 短 TTL、或降低续期频率。本次未改动。
- **进程内缓存（Caffeine/本地 L1）暂不建议**：缓存路径与非缓存路径在饱和点收敛，说明瓶颈已不在 Redis/查询，继续叠加本地缓存在单机压测里无法体现收益，属过度设计。

### 3.3 既有缓存防护与本次矩阵的关系

- `/goods/list`：60s HTML 缓存 + 击穿保护（SETNX 单飞锁，实测 15 并发重建全部 200）；
- `/goods/detailStatic/{id}`：穿透保护（空值缓存 60s）+ 1s/10/IP 限流（故未纳入 QPS 矩阵）；
- 本次压测未发现需要新增的缓存层。

## 4. 提交前建议

- 三个本地提交（`73e1b05` 个人中心、`9d64b64` PC 端 UI、`474c714` 丰富商城）尚未推送；本次压测新增资产（`load-test/jmeter/qps-endpoints.jmx`、`run-qps-matrix.sh`、`jtl-stats.js`、本报告）也未提交。
- 建议步骤：先提交压测资产 → 二选一：(a) 直接推 `main`（个人仓库，v1.0.0 tag 已推送）；(b) 按模板建议开 `test/qps-validation` 分支验证后再合并。**执行前请确认选择，未经确认不会推送。**

## 附录：复现命令与原始工件

```bash
# 应用（本机）
REDIS_PORT=16379 java -jar target/seckill.jar

# 压测矩阵（7 场景，需在仓库根目录执行；Git Bash 下脚本内已处理 MSYS 路径转换）
sh load-test/jmeter/run-qps-matrix.sh

# 统计任意一次结果
node load-test/jmeter/jtl-stats.js target/perftest/list-50.jtl
```

- 原始工件（不随 git 保留）：`target/perftest/{list-50,list-100,list-200,listnocache-50,listnocache-100,userinfo-50,userinfo-100}.jtl` 及同名 `.out/.log`。
