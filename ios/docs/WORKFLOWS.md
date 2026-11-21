# GitHub Actions 隔离审计

审计对象：以 f2cb3cb021429460d9f8c2f3a1a13b64bbbd221a 为起点的 iOS 分支。这里只读取现有工作流；本次没有执行、改写或复用 Android 签名发布流程。

| 原有文件 | 实际作用 | iOS 处理 |
| --- | --- | --- |
| cattv-release.yml | 指定手机发布分支/手动触发，Android Release 构建、签名和创建 Release | 不修改、不触发、不引用其凭据 |
| cattv-release-once.yml / cattv-release-once-v2.yml | 历史一次性 Android 签名发布 | 保留原状 |
| release.yml | 手动 Android Release 构建和 Release 更新 | 保留原状 |
| build-debug-apk.yml | 名称与文件名不完全一致，包含 Debug 单测和优化的 Release APK 构建、artifact | 保留原状，不用文件名推断它只是 Debug |
| mobile-panconfig-lifecycle.yml | fix/mobile-panconfig-lifecycle 分支验证，Debug 单测与 Debug/Release APK | 不在 ios/native-port push 的触发分支内 |
| update-v132-notes.yml | 历史版本 Release 文案更新 | 保留原状 |
| notify.yml | release/手动触发，读取 APK 并通知 | 本次无 Release 发布，不触发通知 |
| dependabot-auto-merge.yml | Dependabot PR 检查及自动合并 | 未创建 Dependabot PR，不改变该策略 |

新增 ios-build.yml 的 push 限制为 ios/native-port 且只关心 ios/** 和自身工作流；PR 路径过滤同样独立。运行于 macOS，只使用 contents:read；执行 Swift 测试、XcodeGen、模拟器编译和 UI 测试、设备 Release 无签名编译、IPA 打包和结构检查，并上传构建/测试 artifact。没有 gh release、Android Gradle 调用、签名密钥注入或正式发布步骤。

每次交付需检查相对 Android 起点的改动路径；本轮 `git diff --name-only <baseline> -- app quickjs pyramid gradle libs` 为空。远程写入仅限用户授权的独立分支，不合并 Android/TV 分支。

未知项仍须按实物核验：已发布 1.3.6 APK 与此分支起点的逐文件对应、GitHub 仓库运行时的外部策略/分支保护和历史发布凭据权限。没有读取或导出任何私钥来补足这类核验。
