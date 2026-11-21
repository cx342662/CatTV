# CatTV iOS 移植开发版

本分支仍在移植和验收，不能作为与 Android 全功能等价的正式版发布。Android/TV 源码、签名及原有更新流程保持独立。

## 已写入的实际功能

- SwiftUI 首页、记录、追剧、我的；复用 Android 导航矢量路径和猫咪图案。
- HTTP(S) 配置导入、手动 JSON 编辑、多配置切换/删除、站点切换与选择持久化；保留原始 JSON。
- XML type=0、JSON type=1、扩展 API type=4 的分类/搜索/详情接线；筛选、分页、请求头、扩展 GET/POST。
- 线路、选集、历史、追剧、续播位置、上下集、自动下一集和定时停止。
- 直播内联频道、M3U/TXT 列表、分组搜索、频道/线路切换；XMLTV/JSON 节目单与用户自定义地址（验证状态见功能表）。
- AVPlayer 播放、系统全屏/PiP/AirPlay 接线；应用级会话、后台音频声明、锁屏命令及音频中断处理。
- 请求头媒体代理、HLS 主/子列表、分片、KEY/MAP 地址重写和 Range 流式转发。
- WKWebView 的 playUrl / type=4 parse/jx 网页媒体发现；保留媒体 cookie/Referer 后进入播放链路。
- 内置媒体徽章、徽章包导入/选择/删除与持久化，配置二维码生成/分享/扫描，网络缓存查看和清理。

“已写入”不等于已完成真实源、UI 或真机验收。受控测试配置通过普通导入入口加载，网页解析后的测试视频已在模拟器显示实际画面；测试媒体没有打包进 App。详细范围及未完成项见 [功能对照表](docs/FEATURE_MATRIX.md) 和 [审计与构建证据](docs/AUDIT.md)。

## 主要缺口

JAR/QuickJS/Python 爬虫运行时及宿主桥未移植；网页媒体发现不是爬虫运行时。root parses 已实现 type=0 网页和 type=1 JSON 解析、cat_ext 头、并发尝试及手动选择；type=2/3 聚合解析和特定站点规则尚未移植。安卓播放器控件位置、手势、方向策略和整体主题尚未完全对齐。额外播放内核、直播回看、弹幕、字幕管理、视频预缓存、音乐和其他 Android 专属入口仍有缺项。

请求头代理默认仅监听 loopback，此模式不向 AirPlay 接收器提供代理地址。用户可主动开启局域网媒体代理，绑定局域网接口并使用临时不透明路由；关闭会停止当前局域网代理播放。此路径仅选用 Wi-Fi/有线 IPv4，网络权限和真实接收器尚未验证。直接媒体地址保留系统 AirPlay。没有真机时，不把后台、PiP、锁屏、相机、AirPlay 和重签安装标为验证通过。

## 构建与测试

macOS 安装 Xcode、命令行工具和 XcodeGen 后，从仓库根执行：

```sh
swift ios/scripts/prepare-app-icon.swift
cd ios
swift test
xcodegen generate
xcodebuild -project CatTV.xcodeproj -scheme CatTV -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build/device CODE_SIGNING_ALLOWED=NO build
```

独立工作流 `.github/workflows/ios-build.yml` 执行核心测试、模拟器编译、应用导航 UI 测试、无签名设备 Release 编译及 IPA 打包。每个提交以自己的 Actions 结果为准，不能沿用旧提交的成功状态。

## IPA 签名和安装

成功构建会上传 `CatTV-iOS-unsigned` artifact，包含 `CatTV-unsigned.ipa` 和对应 SHA256 文件。下载外层 ZIP 后解压取得 IPA。

1. 先验证 IPA 的 SHA256 与随附文件相符；外层 artifact ZIP 和内层 IPA 的摘要不同。
2. 这个 IPA 未签名，不能直接安装。通过用户选择的兼容工具，为 App 与任何嵌套代码重新签名，并加入与签名账号、bundle ID、设备和权限相匹配的有效描述文件。
3. 签名账号、有效期与设备限制由所选签名方式决定。此仓库不集成商业签名服务，也不需要提交私钥、证书密码或账号口令。
4. 真机重签安装尚未完成验证；Windows 操作路径见 [自行签名说明](docs/SIGNING_WINDOWS.md)，根据工具官方文档整理，未冒充本 IPA 实测。模拟器编译通过也不能证明 IPA 可安装。

## 数据与许可

不内置影视源、影视资源、私人配置或签名凭据。用户配置、历史和徽章包仅在运行后存入应用私有目录；网页解析使用非持久 WK 数据存储。清理网络缓存不会删除配置或历史。

新增代码与派生图标遵循根目录 AGPL-3.0。运行期目前只使用 Apple 系统框架，没有加入第三方播放器/爬虫二进制。构建工具 XcodeGen 使用 [MIT 许可](https://github.com/yonaskolb/XcodeGen/blob/master/LICENSE)，不打入 App。原 Android 的全部传递依赖及本地二进制许可审计仍未完成，不能标为已全面审查。
