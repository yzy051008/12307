# 12307 JMeter 购票测试

## 文件

- `purchase-v2-concurrency.jmx`：3 个不同账号并发购票。
- `purchase-v2-idempotency.jmx`：同一账号的 3 个完全相同请求并发，用于观察 REST 锁。
- 对应 CSV 文件提供测试账号、密码和乘客 ID。仅用于本机测试，不要提交到公开仓库。

## 运行前

1. 确认本地 Docker 后端服务和 Gateway 已启动（Gateway：`http://127.0.0.1:9000`）。
2. 在 JMeter 的 `Test Plan > User Defined Variables` 确认 `trainId=2`、`departure=北京南`、`arrival=杭州东`、`seatType=2` 指向当前有余票的样例。
3. 购票会创建待支付订单并占库存。记下测试前库存；只运行本地测试，不要支付。
4. 每份计划设置为 3 线程、1 秒 Ramp-Up、单次循环，购票前同步放行。

## 运行

用 JMeter 打开对应 `.jmx`，确认变量后点击绿色启动按钮。`Aggregate Report` 显示汇总；逐请求结果分别写到 `results/purchase-concurrency.jtl` 和 `results/purchase-idempotency.jtl`。

正式重跑前先清空 JMeter 监听器结果，并移走或改名已有 `.jtl`。结果文件不会保存请求头或响应体，避免记录 Token。

## 判断

- 成功以响应 JSON 的 `code` 为 `0` 判断；计划中已有断言，不以 HTTP 200 单独判定。
- 不同账号测试的成功订单数不得超过测试前可售库存。
- 同账号请求确实重叠时，预期一个成功，其余收到防重错误。REST 锁请求完成后释放，所以之后重发仍可能成功。
- 若库存高于 3，这轮只能验证 3 个请求同时到达的行为，不能据此宣称高并发或验证库存耗尽时的超卖。

## 余票不足时的并发抢票

- `purchase-v2-oversell.jmx`：20 个不同账号同时抢 `trainId=1`、G35、北京南→杭州东、商务座（`seatType=0`）。账号和各自关联乘客来自 `purchase-oversell.csv`，共 20 组。
- 准备计划时本机 Redis 和数据库都显示该席别余票为 9 张；运行前必须重新确认库存仍为 9。计划不会重置或修改库存。
- 在 JMeter 打开该 JMX 后可直接点绿色启动按钮。它会先登录 20 个账号，再由同步定时器尽量同时发出 20 个购票请求。
- 预期最多 9 个请求返回业务 `code=0`，其余因余票不足失败；断言会把这些预期失败计入 JMeter 的 Error。若实际库存不是 9，成功数上限随之变化。
- 购票会创建待支付订单并锁定座位。此计划只运行一轮；不要支付，也不要在旧订单尚未清理、库存未恢复前重跑。结果写入 `results/purchase-oversell.jtl`。

## 单账号幂等性测试

- `purchase-v2-idempotency-once.jmx`：一个账号、同一个乘客、同一购票请求同步发送 3 次。账号行在 `purchase-idempotency-once.csv` 中重复 3 次。
- 目标为 G39（`trainId=2`）北京南→杭州东二等座（`seatType=2`）；准备时 Redis 余票为 810，运行前应再次检查。
- 导入 JMX 后可直接点绿色启动按钮。若请求确实重叠，预期一个请求成功、重复请求被幂等锁拒绝；锁只在请求处理期间有效，所以若请求未重叠，不能只凭多个成功就断言幂等实现失效。
- 成功请求会创建待支付订单并锁座；计划只运行一次，不要支付。结果保存到 `results/purchase-idempotency-once.jtl`。
