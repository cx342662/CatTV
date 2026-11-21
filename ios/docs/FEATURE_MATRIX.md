# iOS 移植功能对照（持续更新）

状态解释：代码接线不等于验收；核心测试不等于真实源播放；模拟器不等于真机。此表不将未验收功能计为已完成。

| 功能 | Android 依据 | iOS 实现 | 当前验证范围 / 缺口 |
| --- | --- | --- | --- |
| 首页、四个导航入口 | ui/page/MainScreen、HomePage、HomeGridLayout | CatTVApp | 早期导航 UI 已通过；新悬浮导航和详情隐藏导航的测试进行中，整体视觉尚未逐像素比对 |
| 图标资源 | drawable/ic_tab_*.xml、cattv_launcher_foreground.png | SVG 路径复用及构建时生成 AppIcon | 四枚导航图标、直播入口及首页猫图案复用；尺寸对齐后的截图待验收 |
| 配置导入、编辑、切换、删除 | api/ConfigParser、ui/page/ConfigManagePage | ConfigurationCollection、ConfigurationEditor、LibraryModel | 原始 JSON 保留、无效数据回滚有核心测试；相对 api 地址可解析 |
| 站点请求头 | ConfigParser.parseHeaderObject、SourceHelper.siteGet | HeaderPolicy、CatalogRepository | 原始值归一化、控制字符拦截有测试 |
| XML 源 type=0 | SortLoader、ListLoader、SearchLoader、SourceResultParser | XMLCatalogDecoder、CatalogQuery | 分类、CDATA、分页元数据、线路及选集有测试 |
| JSON 源 type=1 | 同上 | CatalogRepository、CatalogResponse | 基础分类、搜索、详情接线；尚待用户真实配置逐项对照 |
| 扩展 API type=4 | SortLoader.sortFromExtendedApi、PlayLoader.playFromExtendedApi | CatalogRepository、PlaybackResolver | filter/ext/extend、长扩展 POST、播放结果 parse/jx 路由；未覆盖所有扩展服务变体 |
| 分类筛选、分页 | ListLoader、SourceResultParser.sortJson | CatalogQuery、LibraryModel | base64 JSON 及页码编码有测试；页面接线待 UI/真实源验收 |
| 网页解析 | WebViewJs、WebParseUseCase | WebMediaSniffer | WKWebView 脚本执行、媒体元素/Performance API、媒体 MIME 导航；受控页面→媒体发现→请求头代理→AVPlayer 实际播放已通过；真实站点及跨域 iframe 待验证 |
| 自定义媒体请求头 | PlayLoader、SourceHelper.extractPlayHeaders | MediaHTTPProxy | 真正回环 HTTP 测试覆盖请求头、Range、206、HLS 列表及分片，不只是字符串模拟 |
| 主列表、分片、密钥地址 | M3u8、WebParseUseCase | HLSPlaylistRewriter | 相对路径、主/子列表、KEY、MAP、内嵌 data/skd URI 保留；DRM 本身未实现 |
| 播放进度、历史、追剧 | data、HistoryPage、FollowingPage | UserLibrary | 原子落盘接线，持有 source 快照；完整迁移、更新追剧元数据及真机续播待验收 |
| 上下集、自动下一集 | PlaybackController / player 状态 | PlaybackModel | AVPlayerItem 结束事件及远程上下集接线；需真实媒体运行验证 |
| 后台音频、锁屏 | PlaybackService、MusicSessionDelegate | 应用级 PlaybackModel、MPRemoteCommandCenter | 播放会话、锁屏命令、打断/耳机拔出处理；无真机验收 |
| 画中画 | Android PiP 逻辑 | AVPlayerViewController | 已启用系统 PiP；返回页面恢复及系统回收待真机测试 |
| 全屏、旋转 | DetailActivity、VideoOrientation、ui/player | AVKit 系统全屏、声明横竖屏 | 未完成安卓按钮位置、方向策略及自定义手势逐项对齐 |
| AirPlay | Android 投屏为 DLNA 路径，不是 AirPlay | AVPlayer 外部播放 | 直接地址开关已启用；默认 loopback 代理关闭外部播放；另有用户主动开启的局域网 IPv4 代理路径，权限/接收端待真机验收 |
| 定时退出 | PlaybackStopTimerState、PlaybackStopTimerSheet | PlaybackModel timer | 定时停止并释放音频会话；后台长时间运行待验收 |
| 媒体徽章 | MediaBadges、MediaBadgePackParser、MediaBadgeSettings | MediaBadgeMatcher、BadgeStore | 内置规则及 HDR 分组优先级有测试；包导入/持久化接线；SVG 图片回退文字，RE2 与 ICU 规则差异待对照 |
| 二维码 | Android ZXing、配置中心 | VisionKit、CoreImage | 配置 URL 扫描、展示、分享及相机错误处理；没有真机相机验证 |
| 缓存 | Android 播放磁盘预缓存 | CacheSettingsView、URLCache | 只覆盖网络缓存查看/清理；视频预缓存尚未移植 |
| 直播/EPG | LiveEpgParser、LiveEpgController、直播频道模型 | LiveCatalog、LiveView、EPGDecoder、EPGRepository | M3U/TXT、内联频道、多线路和频道切换已写入；节目单 XMLTV/JSON、GMT+8 日期及午夜处理已写入并提交测试；受控点播、直播及节目读取 UI 在 6aff275e 通过；时区显示与导航点击的后续修正另做回归，回看尚未移植 |
| 构建及无签名 IPA | 独立新工作流 | ios-build.yml、XcodeGen | 早期开发版已有成功产物；每批新增代码的验证记录需看对应 Actions |


## Android 运行时与尚未移植的能力

- **JAR 爬虫**：SpiderLoader/JarLoader 通过 Android/JVM 动态加载类，并依赖 Android Context、宿主 Java 类型和字节码签名。Swift/AVKit 无此 ABI，不能打包 JAR 后直接运行。可行替代是逐源原生适配，或用户自控兼容服务输出 type=4；本分支尚未提供该服务，不能声称已兼容。
- **QuickJS 爬虫**：现有 JsLoader 不只是执行一段 JavaScript，还提供模块、网络、HTML 解析和原生桥。WebMediaSniffer 是网页解析器，不能替代 QuickJS 爬虫。需要移植运行时及桥接契约并验证依赖许可证，目前未完成。
- **Python 爬虫**：pyramid/Chaquopy 与 Android 宿主及 Java 桥相连。没有将 CPython 与对应桥移植到 iOS，不能把原 py 文件视作已支持。
- **解析器集合**：root parses 已实现 type=1 并发 JSON 解析、type=0 网页回退/选择与 cat_ext 请求头。type=2/3 聚合解析、特定站点点击脚本/规则尚未完成；当前实现不是完整解析器兼容。
- **播放器其他内核/协议**：Media3/FFmpeg、IJK、RTMP/RTSP、DASH、蓝光 ISO/m2ts、软件解码和平台特有画面效果没有由 AVPlayer 完整替代。若引入额外内核，需要单独审计其版本、构建链与许可；当前没有把未经审计的二进制加进 IPA。
- **其他入口**：直播回看、音乐/歌词、弹幕、独立字幕管理、TMDB/Douban 衍生信息、WebDAV/SMB、电视遥控/局域网配置服务、预加载、画面增强和完整主题设置尚未逐项移植，不能省略后宣称 Android 等价完整版。

## 平台限制与替代方案

代理媒体默认只监听 127.0.0.1。现有可选局域网模式要求用户主动开启，监听局域网接口、选取 Wi-Fi/有线 IPv4、使用不透明随机路由，并在关闭时停止相关播放。此模式尚未验证真实 AirPlay 接收器，IPv6-only 网络和热点接口尚未支持。

没有苹果设备不影响云端编译和模拟器测试，但相机、后台系统策略、锁屏、AirPlay、PiP 与 IPA 重签安装仍需后续真机验收。
