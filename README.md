<p align="center">
  <img src="./icons/icon-rounded-square.png" width="128" alt="nonoti icon">
</p>

<h1 align="center">nonoti</h1>

<p align="center">
  <strong>Messages arrive on their schedule. You process them on yours.</strong>
</p>

<p align="center">
  一个把随机通知集中到你选择的时间再处理的 Android 工具。
</p>

## 少一点打断，多一点主动

nonoti 不会让消息消失。它在 Focus 期间安静地收集通知，并在 Focus 结束后一次性交还决定权。

- **Focus** — 设置每日防干扰时段，或立即开始一次临时 Focus。
- **Box** — 集中查看 Focus 期间收到的消息。
- **Quiet Release** — 原通知静默恢复，只发送一次汇总提醒。
- **Emergency Access** — 紧急时临时进入 Box，不结束当前 Focus。

## Local first

通知内容只保存在设备本机。v0.1.0 不需要账号、服务器、云同步或网络权限。

## 当前状态

v0.1.0 为预发布版本，尚未完成真实 OEM 手机验收。GitHub Release 中的 APK 未签名，需自行签名后才能安装。变更与验证范围见 [CHANGELOG.md](./CHANGELOG.md)。

nonoti v0.1.0 已实现 Android 13+ 的 Focus、Box 与 Settings 完整流程：分钟级每日计划、跨午夜、Start now、连续 Session 合并、长按删除、Emergency Access、通知过滤与去重、Quiet Release、Room 状态恢复、Automatic Zen Rule、权限失效 Fail Open 和设备兼容性自测。

未完成 Notification Access、勿扰、精确闹钟、汇总通知权限或设备自测时，应用会阻止正式 Focus。系统大版本、ROM、设备指纹或应用版本变化后必须重新自测。

工程采用 Kotlin、Jetpack Compose、Material 3、Room、DataStore、Hilt 和 Navigation Compose 构建。

## 验证

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

单台模拟器的完整验证：

```bash
scripts/verify-emulator.sh emulator-5554
```

当前 22 项设备测试已在 API 33、34、35、36 和 37 Google API/Play Store 模拟器通过，覆盖真实外部包通知捕获、同 key 更新、来源取消、实时/持续通知排除、分组通知、Zen 生命周期、Quiet Release、连续 Session 延长、异常恢复、时间变化、Room 重开与跨进程去重、时间选择和 Focus Block 删除。小屏、横屏、1.5 倍字体及亮暗主题已人工检查。

恢复与 Doze 验证：

```bash
scripts/verify-recovery.sh emulator-5554
scripts/verify-doze.sh emulator-5554
```

恢复脚本覆盖真实进程重建、通知权限撤销、精确闹钟权限撤销和 Notification Access 撤销；Doze 脚本覆盖熄屏、deep Doze 和外部包通知捕获。两项脚本均已在 API 33、34、35、36、37 模拟器通过。

模拟器不能证明真实 OEM 对声音、震动、Heads-up、状态栏闪现、电话、闹钟、Doze 和后台限制的行为。正式发布仍需至少一台 Android 13+ OEM 真机完成应用内兼容性自测和发布矩阵；未通过自测的设备会被应用阻止启动 Focus。

## Contributing

开发约定与验证要求见 [AGENTS.md](./AGENTS.md)。

## License

本项目采用 [Apache License 2.0](./LICENSE)。第三方依赖保留各自的许可证。
