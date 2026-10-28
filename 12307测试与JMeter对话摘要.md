# 12307 项目测试与 JMeter 对话摘要

整理日期：2026-10-28

## 项目与目标

- 当前项目是 12307 铁路购票项目，用于准备实习简历和面试讲解。
- 用户已跑通购票到支付的流程，并确认订单支付后票数会扣减。
- 测试重点是核心购票链路、库存竞争、同用户并发防重，以及可复现的 JMeter 测试结果；不要把小规模测试夸大成高并发结论。

## 已讨论的接口与 Token

- 登录：`POST /api/user-service/v1/login`，JSON 请求字段为 `usernameOrMailOrPhone`、`password`。
- 登录响应中的 `data.accessToken` 用作后续请求凭证。JMeter 的 HTTP Header Manager 添加：

  | Name | Value |
  |---|---|
  | `Authorization` | 登录响应的 `data.accessToken` 原值 |

- 项目生成的 `accessToken` 已带 `Bearer ` 前缀，复制完整值即可，不要重复添加前缀。
- 多账号测试时，每个虚拟用户需要自己的 Token。可用 CSV Data Set Config 读取 Token，Header Manager 的 Value 写 `${token}`。
- 购票 V2 接口：`/api/ticket-service/ticket/purchase/v2`。
- 票务查询曾使用：`/api/ticket-service/ticket/query`；订单查询曾使用：`/api/order-service/order/ticket/query`。
- HTTP 200 不一定表示业务成功：项目可能在响应 JSON 中返回业务失败。检查响应体里的业务成功标志/错误信息，不要只看 HTTP 状态码。

## 幂等与两种并发测试

### 不同账号抢同一库存

验证不同用户同时竞争同一车次、同一席别时是否超卖。

- 例如 3 个账号：Thread Group 线程数 `3`、Ramp-Up `1` 秒、循环次数 `1`。
- 添加 Synchronizing Timer：模拟用户数 `3`，超时 `10000` 毫秒，让三个线程尽量同时发请求。
- 三个账号使用各自 Token 和乘车人数据，购票请求指向同一车次和席别。
- 判断时结合响应体、测试前后的库存和订单/占座状态。成功数不能超过可售库存，库存不能为负。
- 3 个账号只能做小规模并发验证；不能据此宣称完成了高并发压测。

### 同一账号重复并发请求

验证同一个用户的请求同时到达时，购票接口的 REST 防重锁是否生效。

- 多个线程使用同一个账号 Token，并发送完全相同的请求数据。
- 这验证的是请求重叠期间的防重；请求处理结束后再重新发送，不一定会被拦截，因为 REST 锁会在请求结束后释放。
- HTTP 重复请求与 MQ 重复消费是不同场景。MQ 消费幂等要单独验证消费者重复收到同一消息时的处理结果。

## JMeter 已做内容与结果

- 使用 Apache JMeter `5.6.3`。
- 曾用 5 个线程、每线程循环 10 次测试查询接口；清理旧结果后的一次报告为：

  | 指标 | 结果 |
  |---|---:|
  | Samples | 50 |
  | Average | 8 ms |
  | Median | 7 ms |
  | 90% Line | 10 ms |
  | 95% Line | 20 ms |
  | 99% Line | 26 ms |
  | Min / Max | 5 ms / 26 ms |
  | Error % | 0.00% |
  | Throughput | 6.2 次/秒 |

- 这组数据是小规模查询基线，不是购票高并发结果，也不能证明购票接口的库存一致性。
- 曾出现 JMeter“文件已存在”的提示：Append 会把新旧结果混在一起；做干净的一轮测试应先清空监听器结果，文件提示时覆盖旧结果，或改用新的结果文件名。
- Aggregate Report 的表格 CSV 适合保存汇总指标；`.jtl` 更适合保留每个请求的时间、状态和响应数据。`.jmx` 保存测试计划和参数。
- 当时 JMeter 窗口标题显示测试计划在 JMeter `bin` 目录下的 `View Results Tree.jmx`。项目文件列表里没有确认到已保存的 `.jmx`、`.jtl` 或报告 CSV；需要将最终测试计划和结果另存到项目目录并核实文件确实存在。

## QPS 测试

- JMeter 可以做 QPS/吞吐量测试。一个循环只有一个请求时，Aggregate Report 的 Throughput 可作为实际请求速率参考。
- 若要持续控制目标速率，可用 Constant Throughput Timer，单位是“样本/分钟”：例如目标 `10 QPS` 填 `600`，并设置足够线程和运行时长。
- Synchronizing Timer 用于让一组请求同时出发；Constant Throughput Timer 用于按目标速率持续发请求，两者用途不同。
- 持续 QPS 测试优先选只读查询接口。购票接口会创建订单或占用库存，适合有限次数的竞争测试，不能当作无副作用的持续流量接口。

## 本地 Docker 环境

2026-10-27 检查到 12307 项目的容器在运行：

- Gateway：本机端口 `9000`
- Frontend：本机端口 `8080`
- MySQL：容器 `12307-mysql-1`，本机端口 `3307`
- Redis：本机端口 `6379`
- Nacos：本机端口 `8848`
- RocketMQ NameServer：本机端口 `9876`

Docker MySQL 可访问；数据库 schema 沿用了 `index12306` 这个名称，订单表采用分表形式（`t_order_0` 至 `t_order_15`）。后续核对需要按项目分片规则查询，不能只看单张订单表。测试结果应结合数据库订单/库存状态，以及项目实际使用的 Redis 库存状态判断。

Apifox 曾出现代理连接失败、DNS 解析失败和连接拒绝。请求本机 API 时应检查环境前置 URL、接口路径和代理设置；容器映射显示 Gateway 暴露在 `localhost:9000`，前端在 `localhost:8080`。

## 下一步

1. 将最终 `.jmx` 保存到 12307 项目目录，并确认购票请求地址、请求体、Token 和线程组配置正确。
2. 测试前记录目标车次/席别的可售库存及相关订单状态；仅使用本地测试数据，不进行真实支付。
3. 先跑 3 个不同账号、同车次同席别的并发请求；确认响应业务结果、库存和订单/占座状态一致。
4. 如需验证 REST 防重，再单独用同一账号和完全相同的请求做并发测试。
5. 保存 `.jtl` 和 Aggregate Report CSV，并注明线程数、Ramp-Up、循环数、运行时间、目标库存和测试时间。
6. 将 `.jmx`、`.jtl`、汇总 CSV 发给助手；助手可通过 Docker 只读核对订单和库存。不要在文件或截图中暴露 Token、密码、手机号等信息。

