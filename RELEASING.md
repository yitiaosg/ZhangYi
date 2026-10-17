# 构建、签名与版本发布

账易的正式发布使用项目所有者明确授权生成的专用 JKS（RSA-4096 / SHA256withRSA / 10000天有效期）。库口令与私钥口令独立随机生成；密钥和口令均不进入 Git。不要丢失或重新生成该发布身份，应用升级需要持续使用同一密钥。

## 签名配置

Gradle 支持二选一：

1. 设置 `ZHANGYI_SIGNING_PROPERTIES` 指向**仓库外**的 Java properties 文件，其中包含 `storeFile`（绝对路径）、`storeType=JKS`、`storePassword`、`keyAlias`、`keyPassword`。
2. 安全设置 `ZHANGYI_KEYSTORE_PATH`、`ZHANGYI_STORE_PASSWORD`、`ZHANGYI_KEY_ALIAS`、`ZHANGYI_KEY_PASSWORD` 环境变量。环境变量优先于配置文件。

文件与所在目录应分别限制为0600和0700，不要把真实值写进终端历史、聊天、构建日志或仓库。签名配置缺少任一必要字段会报错，不会回退为 Debug 签名。没有任何签名配置时，CI和普通开发构建仍生成未签名 Release。

开启签名后，Gradle默认将 App构建目录放在 keystore同级的 `build/` 下，避免AGP的签名中间文件将口令落入源码仓库。可通过 `ZHANGYI_SIGNED_BUILD_DIR` 指向另一个仓库外目录。已签名构建禁止配置缓存；项目缓存也应使用仓库外路径。

## 构建与验证

以下路径仅为示例；替换为本机安全目录，不要把口令放进命令参数。

```sh
export ZHANGYI_SIGNING_PROPERTIES=/安全目录/signing.properties
./gradlew --no-configuration-cache --project-cache-dir /安全目录/gradle-cache \
  testDebugUnitTest lintRelease assembleRelease
```

默认签名输出：keystore同级的 `build/outputs/apk/release/app-release.apk`。未配置签名时输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`，后者不能当作正式安装附件。

```sh
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs /安全目录/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 /安全目录/build/outputs/apk/release/app-release.apk
adb install -r /安全目录/build/outputs/apk/release/app-release.apk
sha256sum /安全目录/build/outputs/apk/release/app-release.apk
```

确认包名 `cn.zhangyi`、版本与公钥证书SHA-256符合既有发布身份，并在真实设备上验证升级、数据与启动。Debug和Release签名通常不同，跨签名安装前先导出账本备份。

## 安全备份

将 JKS、其配置文件和公钥证书作为一组保管；配置文件含密码，应与 JKS 一起放入加密备份容器，并另外将口令存入可信密码管理器。至少保留两份受控备份，其中一份离线存储，验证能恢复和读取。不要上传到 GitHub、Release、Issue、聊天或公共网盘；云开发环境不应作为唯一备份。

源码、公开APK或公钥证书不能恢复丢失的私钥。备份后保留文件权限，防止任何自动同步工具把私密目录同步到公开仓库。Git只保存环境变量名称与读取配置的代码。

## GitHub发布

1. 使用 `gh auth login --scopes repo,workflow` 登录，确认账户与已有的 `yitiaosg/ZhangYi` 仓库；不创建同名新仓库。
2. 检查远程分支、标签与文件，保留既有内容；只做正常推送，不强制推送。
3. 构建、签名校验、敏感信息扫描和更新日志准备后，按项目所有者要求取得推送与发布确认。
4. 检查 `v1.0.0` 标签与Release；已存在时不覆盖、删除或替换。
5. 在确认的main提交创建并正常推送 `v1.0.0` 标签；首次提交包含 `Co-authored-by: Codex <noreply@openai.com>`。
6. 发布附件仅包含正式签名APK、公开SHA-256校验值和必要许可。不要上传密钥、密码、配置文件、用户备份或未签名APK。

```sh
gh release view v1.0.0 --repo yitiaosg/ZhangYi
gh release create v1.0.0 --verify-tag --repo yitiaosg/ZhangYi \
  --title "账易 v1.0.0" --notes-file docs/RELEASE_NOTES_v1.0.0.md \
  dist/zhangyi-v1.0.0.apk dist/SHA256SUMS-v1.0.0.txt dist/THIRD_PARTY_NOTICES-v1.0.0.zip
```

## 后续版本

- 更新 `app/build.gradle.kts` 的 `versionName` 并单调增加 `versionCode`；同时更新 `domain/Models.kt` 的 `Backup.appVersion`，备份格式版本独立处理。
- 更新CHANGELOG；更改数据库结构时新增显式Migration与schema，不使用破坏性清库。
- 运行测试与CI，使用原发布密钥签名并回归真实设备升级、备份恢复及关键页面。
- 检查新版本标签与Release不存在后发布新版本，保留旧版本和附件。
- 依赖变更时复核 `docs/dependency-licenses.json`、THIRD_PARTY_NOTICES及APK内的许可副本。
