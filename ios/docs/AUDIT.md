# iOS 移植审计（进行中）

完整源码审计尚未完成。iOS 应用源码、独立构建工作流和基础单测已写入，编译及 IPA 结果以实际 Actions 为准，尚不能作为完整版交付。

## 已核实

- 默认 main 的 app/build.gradle.kts 仍为 1.3.0 / com.github.avbox.osc。
- origin/fix/mobile-panconfig-lifecycle 的默认版本为 1.3.6 / com.cattv.mobile，本隔离分支以它为起点；仍需与已发布 APK 对照。
- app 为 Kotlin/Compose Android 应用；quickjs、pyramid、libs/backdrop 为独立模块。
- SpiderLoader 依赖 Android Handler/Looper、JarLoader、JsLoader 和 Python loader。JAR 不能由 iOS 原生直接执行；需要独立适配或用户控制的兼容服务，不可声称与 Android 全兼容。
- SourceResultParser 包含 JSON、XML 分类及 filters 解析；新 Swift 数据模型目前只覆盖配置基础字段，尚不支持完整配置语义。
- Android 播放栈包含 Media3、FFmpeg 解码、DASH、HLS、RTSP、RTMP；AVPlayer 是否覆盖具体编码、封装和网络协议需逐项验证。
- 存在 WebViewJs、JsonParallel、WebParseUseCase；WKWebView 不能未经验证替代 Android 请求拦截及桥接。
- 已有 cattv-release.yml 为 Android 签名发布流程，引用 cattv-branding；不得复用其签名或修改它来构建 iOS。
- 根 LICENSE 为 AGPL-3.0；新增代码沿用仓库许可。第三方 JAR/AAR、QuickJS、Python 与播放器依赖仍需完整许可证清单。

## 验收清单

下列功能验收尚未完成（部分已写入代码）：原版界面资源及尺寸对照；配置增删改切换；JSON/XML/JS/JAR/Python 源兼容矩阵；搜索分类筛选详情；线路和选集；历史收藏进度；播放器控件及横竖屏；PiP；后台及锁屏；AirPlay；定时退出；徽章；二维码与配置中心；缓存；异常与隐私测试；无签名 IPA 重签与真机安装。

## 本地验证限制

当前 Windows 没有 swift 或 xcodebuild。实际构建已在 GitHub Actions macOS 上完成，见下述记录。已有模拟器界面和受控测试媒体播放验证；真实用户影视源尚未验证，不能把编译通过当作完整功能验收。

## 已完成的云端构建验证

- 代码提交：23d863ebd6586396bc29ff250f218590353b2a06，分支 ios/native-port。
- Actions：https://github.com/cx342662/CatTV/actions/runs/44797350717
- 核心测试：5 项执行，0 失败；覆盖重复 key、spider 字段、数字 ID、线路选集分隔、错误 list 形状及爬虫拒绝 HTTP 路由。
- 模拟器 Debug 编译：BUILD SUCCEEDED。
- iphoneos Release 无签名编译：BUILD SUCCEEDED。
- IPA 打包与 artifact 上传：成功。
- 下载：https://github.com/cx342662/CatTV/actions/runs/44797350717/artifacts/14658719069
- 外层 artifact ZIP SHA256：dd9853d6e6771458a1e4d7aca5116404e12e034fadf9fb4c81ba8300480edf41。内层 IPA 的 SHA256 文件随包提供，两者不能混淆。

此结果未覆盖模拟器启动、界面截图、真实影视源连通性、重签或真机安装。无签名 IPA 不能直接安装。无正式 Release 发布，无 Android/TV 修改。

## 扩展移植批次的验证记录

功能状态以 FEATURE_MATRIX.md 为准。代码不再限于初始 JSON 直连子集，已加入 XML/type=4、完整多配置基础管理、筛选分页、应用级播放会话、二维码、徽章、媒体请求头代理及网页媒体发现。

- c434a15baf0ba47e9f663885cb544bb1b9f1c90f 对应 https://github.com/cx342662/CatTV/actions/runs/44801206103：核心测试、模拟器编译、模拟器导航/无效 JSON 保存 UI 测试、设备 Release、IPA 打包及产物上传均成功。
- 此后 c42fccd0cf6f7df407708939d569a77c14f2865a 增加真实回环代理测试；核心测试步骤成功，编译/界面测试以其独立工作流为准。
- 498d1cd6c32b217e1a42f79efdfe56d6ddcb5687 增加 WKWebView 网页媒体发现；对应 https://github.com/cx342662/CatTV/actions/runs/44802055465 的核心测试与模拟器编译已成功，记录本条时 UI 测试仍进行中。

以上都不构成 JAR/JS/Python 爬虫兼容、真实影视源播放、安卓视觉一致性或真机系统功能验收。

## 受控端到端播放证据

- aaf08c0fcfa7a96a7c133c290072a9678ed2f8c2 对应 https://github.com/cx342662/CatTV/actions/runs/44848339584，核心测试、模拟器编译/UI 测试、设备 Release、无签名 IPA 均成功。
- UI 测试通过应用的普通配置导入、首页、详情和选集入口请求测试服务；WKWebView 发现媒体、带头代理转发、AVPlayer 解码，断言实际播放时间前进。已人工打开导出的截图，确认渐变测试视频画面及 6 秒进度。不是静态假页面。
- 测试服务运行在模拟器 UI 测试进程，视频由 CI 用 AVAssetWriter 生成，仅属于测试 bundle，生产 App 无默认影视配置或视频。
- 本次截图也显示首页标题/搜索及底栏仍与 Android 有差异，因此后续提交复用原版首页猫图案、悬浮底栏、中央直播按钮及尺寸，并在详情隐藏底栏；以新提交独立验证结果为准。
- 直播 M3U/TXT/内联列表、频道切换和节目单正在新增验证，不借用此轮 VOD 成功状态。
- 依赖、现有本地二进制 SHA256 及未完成的许可证审计见 DEPENDENCIES.md。