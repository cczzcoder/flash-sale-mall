# 数据库脚本说明

## 推荐入口

- **新建数据库**：按 `../readme.sql` 的顺序执行基础表脚本，然后执行 `demo_seed.sql`。
- **已有旧数据库升级**：先执行 `upgrade_to_current.sql`，再执行 `merchant_mvp.sql`，不要逐个执行 `V2` 到 `V7`。
- **商家体系（MVP）**：`merchant_mvp.sql` 创建 `shop` 表并补充 `goods.shop_id`、`seckill_user.role`，新库和老库都需要执行一次；脚本可重复执行。
- **演示数据**：基础表和升级完成后，最后执行 `../demo_seed.sql`。它不会删除用户或订单。

`order_info.sql` 已经是当前完整订单结构，包含支付、收货地址快照、物流和退款字段；升级脚本中的 `ADD COLUMN IF NOT EXISTS` 只是为了兼容旧库。

## 历史脚本

旧的分版本迁移脚本已移除，当前只维护 `upgrade_to_current.sql`。`payment_callback_event.sql` 是旧的独立建表脚本，当前统一由 `upgrade_to_current.sql` 管理。

升级脚本会创建缺失的表、字段和索引，并通过 `information_schema` 检查索引，因此可以重复执行。
