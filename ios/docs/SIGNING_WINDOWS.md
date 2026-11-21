# Windows 自行签名与安装

核查日期：2026-11-21。没有苹果设备也可以保存 IPA、查看源码和云端模拟器测试证据；实际签名安装需要 iPhone/iPad。当前包面向 iOS 16 及以上的 arm64 设备，不能在 Windows 上直接运行。

## 取得与检查 IPA

从对应提交的 Actions 成功运行下载 `CatTV-iOS-unsigned` artifact，解压外层 ZIP，取得 `CatTV-unsigned.ipa`、SHA256 和包检查报告（后续构建提供）。不要直接把 artifact ZIP 交给签名工具。

PowerShell 在包所在文件夹执行：

```powershell
Get-FileHash -LiteralPath .\CatTV-unsigned.ipa -Algorithm SHA256
Get-Content -LiteralPath .\CatTV-unsigned.ipa.sha256
```

两者摘要应一致。SHA256 只核对文件；包检查报告只验证结构、arm64/iOS device 平台与测试资源排除，不能证明已经签名或安装成功。早期 aaf08c0f 包的展示版本受 XcodeGen 默认值影响为 1.0，后续工程显式绑定 MARKETING_VERSION，不据此混用产物。

## 用户自行选择的 Windows 签名路径

以下步骤根据 [Sideloadly 官方说明](https://sideloadly.io/)整理，本项目没有在真机上验证此 IPA，也不集成或代理该服务。

1. 从该工具官网获取 Windows 版本，并按其页面准备 Apple 官方网页版本的 iTunes/iCloud；依赖和账号要求以该工具当前说明为准。
2. 用 USB 连接自己的 iPhone/iPad，解锁并完成设备配对/信任电脑。
3. 在工具中选择设备，加载解压得到的 IPA，使用自己的 Apple 账号进行签名与安装。账号、双重认证信息和证书只在所选工具流程中处理，不要提交到 GitHub。
4. 开发签名应用如提示需要 Developer Mode，按 [Apple 官方文档](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)在“设置 → 隐私与安全性”开启，并依提示重启/确认。
5. 启动 CatTV 后自行导入合法配置；App 不附带影视源。更新时维持签名账号和 bundle ID 一致，并先备份自己的配置。

签名有有效期，需按工具及账号规则续签；不要把未签名包理解成永久可安装包。另一个可查阅的 Windows 路径是 [AltStore Classic 官方安装说明](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows)，同样未做本 IPA 的真机验收。

## 验收记录

首次取得设备后需记录：系统/设备型号、签名工具及版本、账号类别（不记录账号密码）、构建提交、IPA SHA256、安装/启动结果，以及 PiP、后台锁屏、AirPlay、相机扫码、旋转、配置持久化和续播结果。安装失败时保留去除个人信息后的签名日志；不能靠模拟器成功推断这些项目通过。
