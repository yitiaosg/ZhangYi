# 验证记录

日期：2026-10-17。原生项目和 debug APK 已实际构建，非静态界面或网页封装。

## 构建与静态检查

下文 `verification/` 路径指原始本地交付留档，不随开源源码提交。此次开源整理与未签名 Release 构建结果见 [开源发布检查](docs/OPEN_SOURCE_CHECKS.md)。

完整执行 `clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`，84 个任务成功，构建日志见 `verification/build.log`。

- JDK 17 / Gradle 8.11.1 / Android SDK 35 / Build Tools 35.0.0。
- debug APK 已通过 `apksigner verify`（v2 签名，适用于 minSdk 26）；详见 `verification/apk-signature.txt`。
- Lint：0 错误，13 条 `GradleDependency` 新版本提示。项目有意固定兼容的稳定版本，不在交付时自动浮动更新。无其他 Lint 警告。
- APK 清单未申请 INTERNET、银行卡/支付、存储或通知权限，仅有 AndroidX 的内部 signature 权限；详见 `verification/apk-permissions.txt`。
- 自适应图标包含账单、日历装订和勾选元素；提供 monochrome 图层。
- 已关闭系统自动云备份和设备迁移；数据迁移由用户主动导出/导入 JSON 完成。

## 已运行的业务测试

**39 项 JUnit 测试全部通过，0 失败、0 错误。** 详细 XML 结果见 `verification/TEST-*.xml`。

| 范围 | 已验证内容 |
|---|---|
| 日期 | 31 日短月与恢复、2 月 29 日平年/闰年、季度、半年、跨年周账单、自定义日/周/月/年 |
| 性质测试 | 多个锚点跨 20 年的月末不漂移；范围生成结果与完整序列切片一致 |
| 时区与历史 | 改变默认时区不改变账期；过去首次日期的两种记录方式；今天待付、次日逾期 |
| 单期覆盖 | 首期30→10、仅改标题/颜色/备注、明确清空备注、恢复默认值 |
| 向后应用 | 按所选未来到期日生效；不复制未改动字段；保留或明确覆盖后续优惠；实际影响数量 |
| 历史保护 | 修改默认规则保留已付快照和过去待付；改已付应付金额不改实付；付款编辑与撤销 |
| 周期与停用 | 新周期保留历史/预付账单；同一预付日期不重复生成；未来订阅可提前改锚点；停用当天保留、之后停止 |
| 金额 | 最小货币单位精确解析、多币种独立汇总、年费按实际扣款月份统计 |
| 备份 | 规则/覆盖/快照/付款/设置往返一致，恢复后未来生成一致；损坏 JSON、缺字段、不兼容版本、重复 ID、无效关联/金额/快照拒绝 |
| 去重与删除 | 反复生成不重复，同日不同订阅均保留，删除只级联所属订阅 |

## 设备验证

**设备安装和仪器测试未通过验证，不能视为已完成。** 本云环境无 KVM（`emulator -accel-check` 显示 CPU 不支持 vmx/svm），Android 15 模拟器以软件模式启动，但系统服务未完整就绪。APK 已成功推送；PackageInstaller 安装时在系统 `StorageManagerService.allocateBytes` 内抛出 `PackageManagerInternal.freeStorage` 空对象异常，发生于应用启动之前。安装日志见 `verification/device-install.log`。这次未取得可用的应用截图，也不声称已完成真机安装或端到端界面验证。

项目已编译 6 项 Android 仪器测试：`AppSmokeTest`（新增、付款、撤销、底部导航）、`CalendarUiTest`（深色、大字体、同日5笔标记与完整列表）和 `RepositoryTest` 的4项测试（Room 持久化/重复恢复、损坏恢复保护、SQL事务回滚、设置日志重放）。可连接设备后执行 `./gradlew connectedDebugAndroidTest`。

## 已知限制

- 第一版只支持离线本地账本和完整替换恢复；没有合并导入、加密备份、汇率、云同步和系统提醒。
- 备份上限32 MiB；过大的账本会拒绝导出，避免生成无法恢复的文件。其他数据数量、日期范围和长周期预览上限见 README。
- 归档不等于停用；停用和周期重排会移除或替代相应未来未付账单，界面保存前有说明。重新启用会补回停用间隔内的周期账单，不假定已付。
- 尚未在多品牌真机、所有 Android 8–16 系统版本以及所有文件提供器上逐一回归。正式发布前应进行真机体验与升级迁移测试。
