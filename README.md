# 账易（ZhangYi）

简单、清爽的原生 Android 订阅账单管理工具。记录周期性订阅，在日历查看每期账单，了解本月付款与下月预计支出。

应用离线运行，数据保存在设备本地；无账号、广告、付费订阅、服务器、银行卡或支付平台接入。默认中文，首次启动为空账本。

## 主要功能

- **看板**：按自然月和实际到期日期统计应付、实付、待付与下月预计支出；此前逾期单独提示，不同币种分别汇总。
- **日历**：切换月份、回到今天、颜色标记、同日多笔账单和清楚的付款状态。
- **订阅**：每周、月、季度、半年、年和自定义周期；搜索、默认规则编辑、停用、归档、历史账单。
- **账单与付款**：单独修改某一期；未来未付账单可向后应用，默认保留已有优惠；手动确认、编辑及撤销付款，应付与实付独立。
- **设置与备份**：深浅主题、Material You 动态配色、默认币种、系统文件选择器导出完整 JSON 与校验恢复。
- **可靠账期**：始终从原始日期锚点计算，正确处理31日、月末、闰年与跨年；不用30天或365天代替月份、年份。

## 安装与使用

支持 **Android 8.0及以上**（minSdk 26），当前版本 **1.0.0 / versionCode 1**。

正式签名 APK 通过 [GitHub Releases](https://github.com/yitiaosg/ZhangYi/releases) 提供。v1.0.0 为首个开源版本；在发布页面下载 `zhangyi-v1.0.0.apk`。构建检查中的未签名 APK 不能直接安装。

开发体验可自行构建 Debug APK：

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可将已签名 APK 传入手机，按系统提示允许对应文件来源安装。不同签名的安装包不能直接覆盖；换签名前务必导出备份。

1. 在看板或订阅页点击“新增订阅”，填写名称、金额、周期与首次扣款日期。
2. 首次日期在过去时，默认从今天起记录，也可从首次日期补记；历史账单不自动视为已付。
3. 在日历或看板打开账单，确认实际付款金额与日期；需要时可撤销。
4. 默认规则在订阅详情编辑，本期特例在账单详情编辑。未来未付账单可选择向后应用，保存前会显示范围与冲突处理。
5. 停用截止日当天保留，之后停止新账单；归档只收起订阅，不自动停用。重新启用会补回停用间隔的账单。
6. 定期在设置导出备份。JSON**未加密**，完整恢复会替换现有数据，卸载应用会移除本地数据。

## 从源码编译

技术栈：Kotlin、Jetpack Compose、Material 3、Navigation Compose、ViewModel、StateFlow、Coroutines、Room 与 DataStore，单 Activity。

- JDK **17**；Android Studio Meerkat（2024.3.2）或兼容的新稳定版本。
- Android SDK Platform **35**、Build Tools **35.0.0**。
- 固定 Gradle Wrapper **8.11.1**、AGP **8.9.2**、Kotlin **2.1.20**、Compose BOM **2025.04.01**、Room **2.7.1**。
- 配置 `ANDROID_HOME`，或在本地 `local.properties` 指定 `sdk.dir`。该文件不提交到 Git。

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug assembleRelease
# 连接可用设备/模拟器后运行端到端与数据库仪器测试
./gradlew connectedDebugAndroidTest
```

无签名配置时，Release 输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`。正式签名从仓库外的配置文件或环境变量读取，开启签名后的构建中间文件也放在仓库外；不会自动创建或替换密钥。配置与后续发布步骤见 [RELEASING.md](RELEASING.md)。

`minSdk 26` 直接支持 `java.time.LocalDate`；`compileSdk/targetSdk 35` 使用固定稳定工具链并适配边到边显示。后续商店发布需按届时政策升级 targetSdk 并回归。

## 自动化与验证

GitHub Actions 在推送 `main`、提交 PR 或手动触发时运行单元测试、Release Lint、Debug/未签名 Release 构建，并保存短期构建产物。工作流只读仓库，不接触发布密钥，不自动发布 APK。

已有39项业务测试通过；设备端验证曾因云模拟器系统安装服务异常被阻断，不计作通过。详细验证记录及限制见 [VERIFICATION.md](VERIFICATION.md)。第一版无云同步、汇率、提醒、合并导入或加密备份。

数据模型、账期统计与数据库升级策略见 [架构说明](docs/ARCHITECTURE.md)。首次发布功能见 [更新日志](CHANGELOG.md)。

## 开源许可、开发者与 AI 辅助开发

开发者与版权持有人为本仓库所有者 [yitiaosg](https://github.com/yitiaosg)，负责维护与发布。原创项目代码和文档按 [MIT License](LICENSE) 开源，版权归项目所有者所有。

**本项目由 OpenAI Codex 辅助开发。** 第三方依赖仍保留各自许可，不因本项目采用 MIT 而重新许可；完整清单、兼容性说明及版权声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
