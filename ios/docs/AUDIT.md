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

所有下列项目均待完成：原版界面资源及尺寸对照；配置增删改切换；JSON/XML/JS/JAR/Python 源兼容矩阵；搜索分类筛选详情；线路和选集；历史收藏进度；播放器控件及横竖屏；PiP；后台及锁屏；AirPlay；定时退出；徽章；二维码与配置中心；缓存；异常与隐私测试；macOS CI；无签名 IPA 重签与真机安装。

## 本地验证限制

当前 Windows 没有 swift 或 xcodebuild，新增 Swift 单测尚未执行。不得把静态检查当作编译通过。已创建独立 iOS Actions，尚待运行结果，尚无验证过的下载产物。
