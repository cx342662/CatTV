# 依赖与二进制审计记录

本记录区分 iOS 实际打包内容、构建工具和 Android 参考依赖。没有把 Android 库复制进 iOS，未解决的许可证来源也不记为已审计通过。

## iOS 当前边界

- `ios/Package.swift` 没有外部 Swift Package；CatTVCore 是仓库内本地包。
- App 使用 Apple 的 SwiftUI、AVKit/AVFoundation、WebKit、Network、MediaPlayer、VisionKit、CoreImage、Foundation、UniformTypeIdentifiers；按 Apple SDK 条件构建。
- 新增代码、复用 Android 矢量路径及猫咪图案遵循仓库根 AGPL-3.0；原始许可证原样收入 App 的关于页面。
- XcodeGen 仅生成工程，不打包到 App。[上游许可证](https://github.com/yonaskolb/XcodeGen/blob/master/LICENSE)为 MIT。Homebrew 安装版本由每次 Actions 日志记录，当前没有固定工具版本，构建重现性仍有改进空间。
- CI 在测试包中生成六秒渐变视频和海报；生产 App 不包含测试视频、测试配置、私人影视源或节目单服务地址。

## Android 参考依赖与 iOS 影响

以 `gradle/libs.versions.toml`、各模块 Gradle 文件为静态依据；此表不是完整 Gradle 解析后的传递依赖 SBOM。

| 范围 | 已读取的版本/实现 | iOS 状态 |
| --- | --- | --- |
| 播放 | Media3 1.11.1、Jellyfin FFmpeg decoder 1.9.0+1、本地 RTMP AAR | 当前使用 AVPlayer；未复制 Android 播放二进制；编码和协议能力有缺口 |
| JS | wang.harlon.quickjs wrapper 3.2.3、quickjs 模块宿主桥 | 未移植；[QuickJS 引擎本身为 MIT](https://bellard.org/quickjs/quickjs.html)，不能据此推断包装层和所有桥依赖已通过审计 |
| Python | Chaquopy 17.1.0、Python 3.10、lxml/ujson/requests/pyquery/加密等包 | 未打入 iOS；多数 pip 包未锁定版本，需解析准确依赖再逐项核对 |
| 网络/解析 | OkHttp 5.5.1、Okio 3.18.2、Gson 2.14.0、jsoup 1.24.1、XStream 1.4.21 | iOS 以 URLSession、Codable、XMLParser 实现部分等效能力；无 Java ABI |
| 数据/UI | Room 3.0.3、MMKV 2.4.3、Compose BOM 2026.09.00、Coil 3.6.3、RE2J 1.8 | iOS 使用原子 JSON 文件、SwiftUI、系统图像加载、ICU 正则；RE2J 与 ICU 兼容性未完成逐条审查 |
| 投屏/存储 | Cling 2.1.2、Sardine 0.9、NanoHTTPD 2.3.1 | AirPlay 与自有媒体代理已接线；DLNA、WebDAV 等不视为等价完成 |

候选额外播放器 [VLCKit 上游](https://github.com/videolan/vlckit/blob/master/README.md)声明 LGPLv2.1。本分支尚未引入它；特定分发版本的第三方编解码器、可重链接要求、二进制来源和构建条件仍须随实际选择核验。

## 现有本地二进制

下列文件只做只读清点，未修改、复制或提交进 iOS 包。SHA256 用于锁定本次审计对象；根许可证本身不能证明这些二进制的再分发权。

| 文件 | SHA256 | 来源/许可状态 |
| --- | --- | --- |
| `app/libs/LibRtmp-Client-for-Android-v3.2.0.m2.aar` | `8fa44f51f823a73807a73fa06495bb0b7d624b0c2aea60675ff37df704b4b7cc` | 需核实准确上游、修改补丁和依赖许可 |
| `app/libs/thunder.jar` | `bb15de396d120075ade896d36cd3274547dd75b35bb43f22358bec3ac1d50387` | 本地未找到独立 LICENSE，来源与授权范围待核实 |
| `app/src/main/jniLibs/arm64-v8a/libxl_stat.so` | `a962c80fcd30469255a6ba3ad6470f79b3681db99d353df507e8e62f6ea394bd` | 来源与授权范围待核实 |
| `app/src/main/jniLibs/arm64-v8a/libxl_thunder_sdk.so` | `52232f48e6511c1a3bc1c23929f87401553419ad5a7864d2d47db04d26834b55` | 来源与授权范围待核实 |

尚需完成：准确版本的所有传递 POM/许可证、pip 锁定和 wheel 元数据、本地二进制上游及源码义务、构建工具依赖清单。当前不能宣称整个 Android 项目已通过完整许可证审计。
