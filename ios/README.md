# CatTV iOS 移植开发版

本分支尚非功能完整版本。不得将编译成功或生成 IPA 等同于 Android 全功能移植完成。

## 构建

macOS 安装 Xcode 和 XcodeGen 后，在仓库根执行：

```sh
swift ios/scripts/prepare-app-icon.swift
cd ios
swift test
xcodegen generate
xcodebuild -project CatTV.xcodeproj -scheme CatTV -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build/device CODE_SIGNING_ALLOWED=NO build
```

独立工作流 `.github/workflows/ios-build.yml` 在本分支推送时执行核心单测、模拟器编译和无签名设备 Release 编译，成功后上传 `CatTV-iOS-unsigned` artifact。未使用 Android 签名或商业签名服务。

## 安装包边界

`CatTV-unsigned.ipa` 是 Payload/CatTV.app 的无签名 ZIP 包，不能直接安装。需要由用户选择的兼容工具重新签名并加入有效描述文件；签名时需使用对应 bundle ID，并为 App 及任何嵌套代码正确签名。是否能安装、签名有效期、账号和设备限制取决于签名方式，尚未验证具体工具或真机安装。不应将此包标为已验证可安装 IPA。请勿将证书、私钥、账号口令或私人配置提交仓库。

## 当前实现与限制

- SwiftUI 四个导航入口，复用 Android 四枚导航图标的原始 path；尚未完成整体视觉对齐及截图验收。
- 用户自行提供 HTTP(S) JSON 配置；原始 JSON 原样落盘，避免丢失当前尚未解释的字段。当前只有单配置导入/替换，尚无完整多配置管理。
- type=1 JSON API 分类、搜索、详情、线路及选集接线；尚无 XML、JS/JAR/Python、type=4、解析器、过滤器、分页、请求头及相对地址完整兼容。
- AVPlayer 可识别后缀的直接媒体地址播放、原生控件、PiP/AirPlay 开关、播放音频会话和 audio 后台声明；尚未完成原版自定义控件、手势、锁屏媒体会话、后台和 PiP 生命周期验收。
- 播放进度、历史、追剧落盘及续播位置，定时停止播放；均待运行测试，尚无自动下一集与跨线路进度继承。
- 媒体徽章、二维码、直播、弹幕、字幕管理、缓存管理及其他 Android 功能尚待迁移，不能宣称完成。

当前无预置影视源、媒体资源、私人配置或签名凭据。

## 许可

新增代码遵循根目录 AGPL-3.0。运行期使用 Apple 系统框架，没有新增第三方播放器或爬虫运行时。XcodeGen 是构建工具（MIT），不打入 App；完整 Android 第三方依赖许可审计仍待完成。导航矢量路径来自本仓库 `app/src/main/res/drawable/ic_tab_*.xml`，可通过 `scripts/import-android-icons.ps1` 重生成。
