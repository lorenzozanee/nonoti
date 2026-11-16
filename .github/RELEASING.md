# 开发与发布

日常开发：功能分支 push → PR → CI 通过 → 合并默认分支。合并不自动发布。

## PR 检查

- `CI`：单元测试、Android Lint、Debug APK 构建；报告与 Debug APK 保存在 Actions artifacts。
- `Android Emulator Tests`：在 API 33、36 Google APIs 模拟器执行 `scripts/verify-emulator.sh`，包含通知监听、勿扰、通知和精确闹钟权限准备。
- PR、默认分支 push 和手动触发会执行检查；其他分支 push 的 job 跳过，避免与 PR 重复构建。默认分支从 GitHub 仓库配置读取。
- Debug APK 仅用于测试；Actions artifacts 有保留期限，面向用户分发的 APK 放在 GitHub Release。

在 Settings → Rules → Rulesets 为默认分支启用：

1. Require a pull request before merging。
2. Require status checks to pass：选择 `Build & Test`、`Emulator Tests (API 33)`、`Emulator Tests (API 36)`；首次 CI 运行后这些名称才会出现。
3. Require branches to be up to date before merging。
4. 禁止 force push 和删除分支；不要配置日常使用的绕过者。

单人维护时，PR 审批人数可以设为 0，保留 CI 门槛。workflow 文件无法自行禁止直接 push。当前工作流未启用 merge queue；启用前需增加 `merge_group` 触发器。

## 固定发布签名

发布密钥保存在本机 `.signing/nonoti-release.keystore`，配置在根目录 `signing.properties`，均被 Git 忽略。不要重新生成或替换这套密钥。请将密钥和配置加密备份到独立位置；丢失密钥会影响已有安装的后续更新。

本地配置格式：

```properties
storeFile=.signing/nonoti-release.keystore
storePassword=<密钥库密码>
keyAlias=<密钥别名>
keyPassword=<密钥密码>
```

本地构建：

```bash
./gradlew :app:assembleRelease --no-configuration-cache
```

环境变量优先于本地配置：

| GitHub Environment secret | 本地配置 | 用途 |
| --- | --- | --- |
| `NONOTI_KEYSTORE_BASE64` | keystore 文件的 Base64 | CI 恢复同一份密钥库 |
| `NONOTI_STORE_PASSWORD` | `storePassword` | 密钥库密码 |
| `NONOTI_KEY_ALIAS` | `keyAlias` | 密钥别名 |
| `NONOTI_KEY_PASSWORD` | `keyPassword` | 密钥密码 |

CI 使用 `NONOTI_KEYSTORE_PATH` 指向 runner 的临时密钥库。缺少配置时 Release 构建失败；Debug 不需要发布密钥。发布工作流校验 APK 的签名证书 SHA-256 与 [release-signing-certificate.sha256](release-signing-certificate.sha256) 完全一致。该文件只有公开的证书摘要，不包含私钥。

旧测试 APK 使用调试证书；切换至发布证书后不能直接覆盖这些安装。卸载会删除应用本地数据，请自行保留需要的内容。之后使用相同发布证书、递增 `versionCode` 的版本可以正常覆盖升级。

## 一次性配置 GitHub

1. 在 Settings → Environments 创建 `release` 环境。
2. 在该环境中设置上表四个 Secrets，使用本机的固定密钥和 `signing.properties`。不要将凭据放进 PR、Issue、日志或 Actions variables。
3. 可用 GitHub CLI 上传密钥文件的 Base64（这条命令会写入远端，仅在配置发布时执行）：

   ```bash
   base64 < .signing/nonoti-release.keystore | gh secret set NONOTI_KEYSTORE_BASE64 --env release
   ```

   其余三个 secret 通过 GitHub Secrets 页面或 `gh secret set <名称> --env release` 的交互输入填写。
4. 环境部署规则允许默认分支和 `v*` 标签。多人维护时可设置发布审批；单人维护不要启用会阻止自己批准的规则。
5. 建议为 `v*` 标签设置 Ruleset，禁止修改或删除已发布标签，并限制创建者。

PR 检查没有签名 Secrets。Release job 从默认分支已包含的标签提交构建；手动重跑时也必须从默认分支选择已有标签。

## 发布版本

1. 通过 PR 更新 `app/build.gradle.kts` 的 `versionName`、递增 `versionCode`，并更新 `CHANGELOG.md`。不要仅在 workflow 中覆盖版本。
2. 合并前确认 PR 的构建、单元测试、Lint 和模拟器检查全部通过。
3. 对合并后的提交创建并 push 带注释的版本标签。标签必须是 `vX.Y.Z` 或 `vX.Y.Z-alpha.N` / `beta.N` / `rc.N`，去掉 `v` 后必须与 `versionName` 一致。打标签和 push 是单独的发布操作，只有明确准备发布时执行。
4. `Android Release` 会重新执行单元测试、Lint 和 Debug 构建，再用固定密钥构建经过 R8 和资源收缩的 Release APK，核对版本和证书，并创建草稿。
5. 草稿附件包含 `NoNoTi-<标签>-release.apk`、`SHA256SUMS.txt` 和 `version.json`。R8 mapping 保存在该次 Actions 的报告 artifact，排查崩溃时及时归档。
6. 下载草稿 APK，核对校验值，并在 Android 13+ 真机安装启动、验证 Focus 和 Quiet Release。后续版本还需验证从上一发布版本覆盖安装。模拟器不能代替 OEM 真机验收。
7. 补充发布说明与验证范围后公开草稿。带 alpha / beta / rc 后缀的标签自动标记 Pre-release；没有后缀也不代表已完成真机验收，必要时手动标记 Pre-release。

不需要每次 PR 都发布。修复递增 patch，新增功能递增 minor，破坏性产品变化考虑 major；`versionCode` 对所有公开测试版和正式版持续递增。GitHub Release 不等于 Google Play 上架；当前流程只分发 APK。

已发布的标签和附件保持不变，有问题发新版本。工作流不会覆盖已有 Release；创建草稿途中失败时先检查远端状态，确认草稿附件后再决定如何处理，不要删除已公开版本来重跑。

## 验证工作流变更

```bash
actionlint .github/workflows/*.yml
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:assembleRelease --no-configuration-cache
```

本地成功不代表 GitHub runner 已执行。首次提交工作流后，在 Actions 页面确认三个工作流的实际结果；Release 需要先配置环境 Secrets 并明确执行标签发布。

参考：[Now in Android workflows](https://github.com/android/nowinandroid/tree/main/.github/workflows)、[AntennaPod workflows](https://github.com/AntennaPod/AntennaPod/tree/master/.github/workflows)、[Android 应用签名](https://developer.android.com/studio/publish/app-signing)、[GitHub Actions 安全](https://docs.github.com/en/actions/security-for-github-actions)。
