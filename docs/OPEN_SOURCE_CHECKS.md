# 开源发布与签名检查

检查日期：2026-10-17。

## 仓库与许可

- 已验证GitHub账号 `yitiaosg`，目标为其已有的公开仓库 [yitiaosg/ZhangYi](https://github.com/yitiaosg/ZhangYi)。发布准备时远程仓库为空、v1.0.0 Release不存在；没有创建同名新仓库。
- 原有24项应用/测试Kotlin源文件哈希与最初交付一致，没有业务逻辑修改。Gradle仅新增本地Release签名读取与隔离。
- 中文README、MIT、AI辅助声明、第三方许可和Actions工作流已准备。104项实际解析依赖均记录许可，Release运行81项为Apache-2.0/BSD-3-Clause，JUnit EPL仅用于测试。
- 原生图标、Room schema和Gradle Wrapper全部保留。

## 签名与构建

- 项目所有者明确授权新建专用JKS。RSA-4096、SHA256withRSA、有效期10000天；库口令与密钥口令使用安全随机源独立生成。
- JKS、口令配置、签名构建中间文件和Gradle项目缓存均位于仓库之外；目录0700，密钥与配置0600。口令不进入终端输出、聊天或Git。
- Gradle通过 `ZHANGYI_SIGNING_PROPERTIES` 或 `ZHANGYI_*` 环境变量读取；缺失字段拒绝签名，密钥/配置/签名输出路径必须位于仓库外；签名构建禁止配置缓存。
- 实际执行签名 `assembleRelease testDebugUnitTest lintRelease` 成功，82个任务全部执行；39项测试0失败，Release Lint0错误、0警告（离线检查）。
- `apksigner verify`：V2=true、V3=true。APK证书SHA-256与JKS导出的公钥证书一致。
- APK为不可调试Release，ZIP完整性、16 KiB zipalign、包名/版本/四种ABI与5份随包许可资源校验通过。

## 正式APK

- 文件：`zhangyi-v1.0.0.apk`（不提交Git，仅作为Release附件）
- 大小：8256819 字节
- SHA-256：`43b21db2a379a11b076730514425fb54c01b780948452661299ed507ca00211f`
- 公钥证书SHA-256：`6776f47a8eb15447104efa9b9b09393de1570cc0a63e378789c1fbc3ce547aa9`
- 包名 `cn.zhangyi`；1.0.0 / versionCode1；minSdk26、targetSdk35。

## 源码与发布边界

- 暂存内容经Gitleaks扫描未检出秘密；并以真实发布口令与密钥字节进行额外检查，禁止把任何匹配内容放入Git。
- Actions通过本地actionlint检查；移除所有签名变量后再次实际执行 `assembleRelease`，50个任务成功，验证CI使用未签名Release的分支正常。不在GitHub保存签名密码，不自动正式发布。
- 仓库分支与Release最终状态以GitHub页面为准；按所有者要求在最终推送/正式发布前确认，不强制推送，不覆盖既有版本或附件。
- 设备端安装/界面回归此前受云模拟器系统安装服务异常阻断，仍不记为通过，详见 [验证记录](../VERIFICATION.md)。
