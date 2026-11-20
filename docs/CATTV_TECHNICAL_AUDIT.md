# CatTV 技术审计与基础质量体系

审计日期：2026-11-20。结论依据是仓库源码、GitHub 分支/Release 元数据和实际 CI；不是手机/TV 真机认证。生产行为保持不变。审计覆盖关键链路抽样，不是全仓安全认证或所有分支逐行审计。

## 1. 基线、分支与交付边界

- main：66a83cafeb9fa771958a0718a2ae9803ff1bdda7。
- 本次测试分支：test/android-ci-baseline，PR #14。基于 main；未合并、未覆盖任何 Release。
- cattv-branding：ae28f411；release/mobile-v1.3.4：0d8b3a6b；fix/mobile-panconfig-lifecycle：f2cb3cb0；fix/pip-fongmi-style：974b97d9。
- TV 分支：tv-v66、tv-source-preview、tv-arm32-preview-1、tv-update-open-source；另有 Dependabot 分支。分支名不是已发布功能的证明。
- 最近正式版本：手机 v1.3.6 的 Release target 为 f2cb3cb0；v1.3.5 为 4227a73b；TV tv-arm32-v70 的 target_commitish 为 main，但这不能证明该 APK 二进制由 main 源码生成，仍需产物溯源。不得将 main 的本地版本号当最新正式版本号。
- main app/build.gradle.kts 默认 1.3.0/code34，cattv-branding 使用版本属性覆盖且默认值不同；本次未改这些值。签名仍由现有 workflow secrets 临时恢复。
- Android 手机工程：app、quickjs、pyramid、libs:backdrop，Kotlin/Compose、Room 3、MMKV、OkHttp/Gson。保留 com.github.avbox.osc 和 com.github.catvod 字节码契约。
- TV v66 为已有 APK + apktool + Java 补丁的单独构建链，不能称为 app 的 TV flavor。CI 复用该链并固定 7f70b5f；没有认证最新正式 v70。
- 当前本地 GitHub clone 网络不可用，使用 GitHub connector 固定提交读取/写入及 Actions 验证；没有读取或提交密钥。

## 2. 自动化现状和本次实施

复用 JUnit4/Robolectric 及已有大量测试，未建立第二套 Android 测试框架。
release.yml 的 push/PR 检查编译、全套单测、lintDebug、TV 编译；cattv-release.yml 与 release.yml 保留原手动入口。
发布源码仍按原流程检出 cattv-branding；同次源码单测失败时仍构建并上传 APK artifact，但阻止 Release 发布/通知。没有新增自动正式发布触发器，也未用真实版本演练失败发布。
测试、编译失败使 CI 失败；lint.abortOnError=false 是存量配置，本次只报告 Lint，不把“任务成功”写成“零错误”。依赖审查不能由静态未引用结果直接决定，动态爬虫可能依赖宿主包。

验证记录：
- https://github.com/cx342662/CatTV/actions/runs/44744803620 ：手机编译、全套单测、Lint 执行、TV v66 编译、报告上传通过。
- 手机编译 5m14s，单测 1m14s，Lint 3m05s。
- 首轮共 1444 测试，3 失败/3 跳过，失败是新增数据库测试加载 Android JNI；改用测试专用同版本 sqlite-framework 驱动后全部测试通过。生产 BundledSQLiteDriver 不变。
- scripts/quality_report.py 汇总计数、失败测试名、Lint 问题、编译状态、耗时；XML/HTML 作为 artifacts。
- 本次继续增加报告生成器的回归测试，防止报告自身掩盖失败；最新运行以 PR checks 为准。

覆盖：
| 范围 | 已有/新增证据 | 未验证边界 |
| --- | --- | --- |
| JSON | ConfigParserTest：正常、多源、字段、缺失字段、空/损坏集合 | 完整导入→MMKV保存→进程重启未测 |
| 收藏 | DatabasePersistenceTest：真实文件 DAO 写读/同主键替换/删/重开 | Repository 重复添加、进程重启未测 |
| 历史 | 同上：集数/线路更新、touch、订阅隔离；HistoryWriterTest 来源键 | 完整播放进度写入 MMKV 未测 |
| 进度 | PlaybackProgressTest/WatchProgressRulesTest 等规则和编码测试 | 设备上重启恢复 |
| 网络/异常 | HttpTest/HttpPolicyTest/RetryPolicyHttpStatusTest 等 | 私人接口、真实断网、数据库磁盘损坏等未认证 |
| 配置管理 | ParseSubscribeTest、ConfigContentDiffTest、LocalConfigPathTest | CRUD/默认源切换的设备端端到端测试缺失 |

## 3. 影视源解析链路

关键文件均在 app/src/main/java/ 下：
- com/github/avbox/osc/api/ConfigLoader.kt：fetchConfigAsync 使用 activeFetches、取消/过期 URL 判断、HTTP 状态检查、回调错误；读文件固定 UTF-8；refresh/apply/restoreCache 管理缓存。它依赖 KV、Application 与宿主本地服务，不是纯跨平台解析库。
- api/ConfigParser.kt：trimJsonObject、parseSites、parseApiCollection、clan/相对路径归一化。parseSites 直接读取 sites.asJsonArray，并将每项转 JsonObject；缺 key/type/api 跳过且记录日志；结构类型错误可向外抛异常，不能宣称 parser 自身统一返回错误对象。应测试上层捕获而非改变解析语义。
- sourcedata/SourceResultParser.kt：JSON/ XML 分别转换到 AbsXml/AbsSortXml；搜索/详情用 sourceKey、searchToken、detailToken 路由；CancellationException 显式重新抛出。过滤项解析失败可继续但丢弃过滤器。
- util/parser/WebViewJs.kt：网页执行/视频判断、JSON 播放地址和 header 辅助；与 Android WebView 绑定。它不是任意 JS 协议适配器。
- com/github/catvod/crawler/JarLoader.kt：DexClassLoader 动态加载、反射 Spider、proxy、站点 jar 路由。JsLoader/JsSpider 则提供既有 QuickJS/Spider 运行时。
- player/engine/MediaSources.kt：header、缓存 key、HLS/DASH/ISO 类型判断和 MediaItem 转换；配置格式支持不等于每个源的接口契约兼容。
- net/HttpClients.kt：DNS/DoH、代理、默认客户端、超时；Cookie/Referer 能力需连同 JsSpider 的 bridge、WebViewCookies、站点 jar 单独确认，不能因存在 header 字段宣称所有站点兼容。

TVBox 风格 JSON 已有实际字段解析能力；XML 原生分支已存在，不应为了统一架构把所有协议字符串转换成 JSON。M3U 是列表协议，M3U8 常是播放清单，两者职责也不应混同。
搜索/分类/详情/播放需要分别定义能力；多线路要保留稳定 lineId、episodeId 和来源，不用 UI 文本作唯一 ID。

## 4. 风险清单（不在本次改业务）

没有确认可直接复现的 P0。以下分类是源码风险严重性，不是已发生事故证明。

| 级别 | 文件/方法 | 证据及影响 | 状态/后续 |
| --- | --- | --- | --- |
| P1 | net/HttpClients.kt: init/setOkHttpSsl，约 371–423 行 | 默认 init 调用的 X509TrustManager 不校验证书，hostnameVerifier 恒 true；配置/动态代码下载经过该客户端时可能被中间人替换 | 源码确认；兼容性专项修复，不在本轮静默改变网络策略 |
| P1 | catvod/crawler/JarLoader.kt: parseJar/download，约157–190/353–376行 | 已缓存文件比较 MD5；重新下载后直接 load，未见再次对摘要验证；下载异常/中断返回目标文件，可能为部分数据 | 源码确认控制流；模拟下载失败/摘要不符验证后，另 PR 做临时文件+原子替换+加载前校验 |
| P1 | catvod/crawler/js/JsSpider.kt: submitAndWait/call，约139–162行 | Future.get 超时捕获后返回 null，不证明底层 JS 无限循环被停止；任务可继续占用线程 | 源码确认等待边界，运行时 interrupt hook 能力尚待验证 |
| P1 | player/PlaybackService.kt: startForegroundSafely/onDestroy | 前台提升失败时记录并重试；常规 service destroy 路径可能保留内核，这是既有策略，后台限制下需检验无声/被杀恢复 | 待真机，不能直接定性泄漏或锁屏黑屏根因 |
| P1 | data/HistoryRepository.kt: getVodInfo | DAO 查询位于解析 try 之前，数据库异常不会被同一 catch 捕获 | 源码确认边界；上层错误提示/崩溃路径需设备故障注入 |
| P2 | sourcedata/SourceResultParser.kt: sortJson/json/xml | 某些错误记录源响应前200字符，可能含令牌；过滤器异常降级为空可造成“分类不工作” | 脱敏和错误分层后续专项 |
| P2 | api/ConfigParser.kt: parseSites | 对顶层与元素类型假设较强，坏配置可能在 parser 抛异常；缺字段只有日志 | 上层加载错误提示与恢复需集成测试 |
| P2 | player/engine/PlayerEngine.kt: createPlayer | bufferTimes 可放大默认缓冲时长至10倍；包含视频效果/ISO复杂链路 | 无内存/耗电实测，不将推断写成性能缺陷 |
| P2 | CI/版本来源 | main、发布分支、TV补丁来源不一致，检查通过不等于所有正式 APK 均覆盖 | 需要正式产物 SHA→源码提交→构建工具清单 |
| P3 | api/data/sourcedata 及全局 KV | 多向依赖/全局状态限制可替换性 | 保留现状，先稳定接口和 fixture，不做大重构 |

性能待测：多 jar fallback proxy 的遍历放大、JS 单执行器队列阻塞、配置刷新并发请求、WebView 重复建立/清理、缓存 header key 的分裂、长时播放内存增长。未采集 profiler/堆转储/流量统计，以上是观测项，不是性能数据。

### 本轮实际检出的并发问题（阻止完整验收）

最新PR运行 https://github.com/cx342662/CatTV/actions/runs/44749366687 中，已有 IsoResolverTest.resolve_singleFlightForConcurrentCallers 失败：expected 2 reads，actual 4；1444项测试，1失败、3跳过。手机编译、TV、3项报告生成器测试均通过，Lint执行完成。报告正确列出了失败名称和断言。
P2：player/engine/iso/IsoResolver.kt 内 IsoVolumeCache.load 的 finally 先 inFlight.remove(key,running)，之后才 put(key,info)。其他线程可在结果缓存写入前观察到“无缓存、无进行中任务”，开始第二次解析；这与失败表现一致。可造成重复范围请求和额外解析，尚无证据表明导致崩溃或内容损坏。它是生产并发风险，不应改弱测试或仅重跑到绿来掩盖。
本轮禁止改播放器业务，因此保留实现及失败测试，另立最小修复PR：缓存发布必须先于释放single-flight占位，并补可控同步的竞态回归测试。未修复前不声称完整验收通过。
同提交push首次运行 https://github.com/cx342662/CatTV/actions/runs/44749354922 曾因Chaquopy 17.1.0解析失败而未进入编译，已仅重试失败任务；不得把首次失败计为单测通过。

## 5. 播放器底层和设备稳定性

gradle/libs.versions.toml 声明 Media3 1.11.1，FFmpeg decoder 扩展独立版本 1.9.0+1，QuickJS wrapper 3.2.3；wrapper 版本不是 QuickJS C 内核版本。
PlayerEngine.createPlayer 使用 ExoPlayer、EngineRenderersFactory、decoder fallback、扩展 renderer；关闭 MediaCodec 异步队列，缓冲配置可调。MediaSources 有 HLS、DASH、普通源/ISO 路由。MP4/M3U8 是否可播仍取决于编码、封装、加密、权限和设备解码器。
PlayerEngine.release 移除 listener 并 release；clearVideoOutput/detachVideoSurface 分离 surface；handlePlayerError 有 HLS/BDAV 等恢复路径。这些代码存在不代表所有生命周期组合无竞态。
PlaybackService 管媒体会话、前台通知、唤醒/WiFi锁；DetailActivity.onResume/onPause 转发 hostResume/hostPause，onDestroy 释放容器并调用 vm.destroyEngine。PlayerAudioFocus 明确有 request/abandon。未把单一 Activity 回调当最终播放器所有权证据。

真机回归（手机与TV分别记录）：
1. 播放→全屏→旋转→返回，检查 surface、位置、声音与控制响应。
2. PiP进入/退出/返回页面，暂停/恢复、切线路，检查黑屏与按钮；覆盖不同系统版本/OEM。
3. 锁屏/解锁、后台10分钟、音频焦点丢失/恢复、通知权限拒绝、系统回收 Service。
4. 连续进出页面50次、切换集数/线路、断网恢复；采集内存和播放器/Surface实例数。
5. HLS/MP4/不同编解码、软硬解/10bit/HDR、长播2小时。
6. 明确产品策略：后台播放和页面销毁后是否应继续；按策略判断释放，不直接把保留内核判为泄漏。
状态：全部待真机。fix/pip-fongmi-style 分支存在，不能未经逐提交/设备验证宣称其修复已在 main 或正式版生效。

## 6. AVBox 上游关系与独立维护

README 明确 CatTV 基于 AVBox 并致谢 TVBoxOS/FongMi。包名、AVBoxTheme、Spider 反射协议、数据存储键和宿主 bridge 是源码继承关系；不是每次运行时远程调用 AVBox。
当前能确认存在 CatTV branding、签名发布流程和功能开发分支；没有逐文件同上游固定提交 diff，因此不能准确统计“独立修改百分比”或逐项断言某功能归属。
上游停止维护不会立即让已安装版本失效，但系统 API、Media3/爬虫契约、依赖兼容、安全修复和新设备适配需要自行承担。
建议逐步拥有源码适配契约测试、稳定数据 schema、PlayerPort 与 SourceAdapter。现有 player.core/ports 可以作为边界起点，不新建第二个内核；catvod 动态 ABI 需 javap 符号守恒。

## 7. 未来多协议/JS架构（设计，未实现）

用户指定的 index.js 类型尚未认证；现有 JsSpider 的存在不能推断兼容这一格式。未增加下载/执行功能。
架构建议：
SourceRegistry（元数据/启停/版本/选中项）→AdapterRegistry（声明协议/能力）→各协议 Adapter→统一领域模型→现有业务层→PlayerPort。
协议标识必须明确：TvBoxJson、XmlApi、M3uCatalog、WebApi、JsProtocolV1、自定义。不得“所有字符串先当JSON尝试”，M3U8播放交给播放内核而不是假装目录源。
接口建议：capabilities、categories、search、detail、resolvePlayback、cancel。异步调用携带 requestId/deadline；旧请求结果不得覆盖新源。
统一模型：
- SourceDescriptor：id、protocol、origin、enabled、version、contentDigest、capabilities。
- Category/Page/SearchItem/Detail：稳定ID、分页信息、标题/封面/扩展字段。
- PlayLine/Episode：lineId、episodeId、原始定位参数。
- PlaybackRequest：uri、headers、cookies引用、mimeHint、expiry、sourceId；不向日志输出秘密。
- SourceError：code、stage、retryable、safeMessage、causeId；区分网络/协议/摘要/执行超时/权限/取消。
先把跨端 fixture 和错误契约稳定，再考虑共享实现。手机和TV都必须跑相同契约集，平台桥接分别验。

JS导入与更新：远程/本地来源→只下载不执行→限大小/编码→识别协议→校验→隔离试运行→登记版本；多个源独立namespace/缓存/Cookie，启停/删除/回滚不影响其他源。
更新使用临时文件、完整校验、原子提交，失败保留上次可用版本；版本号与内容摘要分开，不能仅用更新时间推断更新。
执行器需要真实引擎中断/进程隔离、内存/栈/输出/并发限制；网络采用授权 host/端口/重定向策略，避免任意本地地址访问；默认无文件/系统/任意Java反射访问。Future等待超时不是沙箱。
QuickJS 可评估，但必须核验现有 Android wrapper 是否暴露执行中断和资源预算；iOS可评估 JavaScriptCore/WKWebView，Windows可评估 QuickJS。不同运行时API差异要由host bridge契约测试约束。不要无条件执行未知脚本。

## 8. MD5格式研究结果

尝试只读访问：
- https://9280.kstore.vip/cat/index.js
- https://9280.kstore.vip/cat/index.js.md5
本次网络工具均无法读取；没有取得响应字节、HTTP状态/Content-Type或重定向证据。不推断是32位裸摘要、md5sum行、JSON、大小写、BOM、换行、文件编码或被哈希对象。
待研究：保存原始响应字节及headers，固定时间戳/版本；区分裸32hex、文件名格式、JSON包装，只有证据支持才选择适配器；明确校验原始下载字节还是规范化文本、BOM/CRLF是否计入。
JS与摘要分开下载存在更新竞态，需绑定同一版本/重试上限，不要无限重试；下载/摘要缺失/不符全部拒绝激活并保留旧版。MD5仅是兼容性完整性信号，不是发布者身份认证；长期用可信渠道/签名清单和更强摘要。
现有 jar 的 ;md5; 规则与这个 .md5 示例是不同协议，不能直接套用。

## 9. 跨平台路线（无平台实现）

iOS：Swift/SwiftUI + AVPlayer/AVKit 为初选，WKWebView用于确有需要的网页路径，JS runtime独立于播放。PiP、AirPlay、后台音频需 AVAudioSession/能力配置。macOS Actions可编译和模拟器测试，但签名/设备部署/真实PiP、AirPlay、锁屏与音频路由需证书和协作iPhone真机；没有Mac/iPhone时不能认证完整体验。
Windows：先用同一fixture比较 Qt+libmpv/libVLC、Flutter+native bridge、Tauri+独立原生媒体宿主。Qt适合原生桌面输入/窗口控制；Flutter适合跨端UI但媒体原生插件能力须验证；Tauri的WebView不等于支持所有媒体格式，播放仍需原生内核。先短期技术验证再选型，不提前移植Android内核。Windows CI构建、安装包和签名是将来任务。
可共享：协议schema、测试fixture、排序/去重语义、数据模型、错误码和迁移规范。平台独立：UI、硬件解码、音频焦点/后台、PiP/窗口、系统权限、原生存储适配。网络/缓存可共享契约而非直接复用Android实现。

## 10. 开源项目和许可证

以下为技术选型初筛，不是最终法律兼容性结论。引入前固定commit、核对每个文件与二进制依赖、构建选项及发布义务，不复制未核验代码。
| 项目 | 借鉴重点 | 适用/限制 | 许可证依据 |
| --- | --- | --- | --- |
| AVBox | 既有宿主/源/播放边界 | 当前继承基础，逐提交比对尚未完成 | CatTV LICENSE/README为AGPLv3；上游具体版本需另核验 |
| FongMi/TV | TV交互、播放生命周期、爬虫接口 | 先读架构，不直接搬代码 | 仓库访问到但具体许可未完成逐文件核验，不标记可直接复用 |
| Media3 | Android播放/会话/扩展renderer | Android原生，不是跨平台播放器 | Apache-2.0，官方LICENSE |
| VLC/libVLC | 跨平台媒体宿主 | libVLC可作Windows候选；播放器与库许可不同 | 官方说明VLC GPL、libVLC LGPL，具体版本核验 |
| mpv/libmpv | 高级桌面播放/渲染接口 | Windows候选，打包和GPU渲染需评估 | Copyright依构建选项/组件含GPL/LGPL，不能笼统称MIT |
| FFmpeg | 编解码/封装/协议 | 是媒体库，不是完整UI/播放器产品 | 默认LGPL2.1+，可选GPL/nonfree改变分发条件 |
| QuickJS | 嵌入式JS、模块/资源预算 | 缺少网页DOM；Android wrapper另有许可证 | QuickJS MIT；wrapper及bridge须分别核验 |
| Qt/Flutter/Tauri | 客户端壳层/交互 | 不自动提供TVBox/JS协议兼容 | Qt模块含LGPL/GPL/商业；Flutter BSD-3；Tauri MIT/Apache-2.0，插件另查 |

官方参考（2026-11-20访问）：
- https://github.com/XiaochangXu/AVBox
- https://github.com/FongMi/TV
- https://github.com/androidx/media/blob/release/LICENSE
- https://developer.android.com/media/media3/exoplayer
- https://github.com/videolan/vlc
- https://github.com/mpv-player/mpv/blob/master/Copyright
- https://ffmpeg.org/legal.html
- https://bellard.org/quickjs/
- https://developer.apple.com/documentation/avkit/avplayerviewcontroller
- https://developer.apple.com/documentation/avfoundation/configuring-your-app-for-media-playback
- https://www.qt.io/development/qt-framework/qt-licensing
- https://github.com/flutter/flutter/blob/master/LICENSE
- https://github.com/tauri-apps/tauri

## 11. 优先级和验收缺口

下一步三个独立任务（新增single-flight缺陷纳入第1项）：
1. 最小播放器并发修复专项：先修复 IsoVolumeCache single-flight 发布竞态并稳定回归；随后受控兼容性安全修复：TLS默认验证、动态代码加载前摘要核验和原子更新；先加失败注入测试，不静默破坏旧源。
2. 发布产物溯源 + 手机/TV真机矩阵：将v1.3.6/v70精确绑定源码；完成PiP/锁屏/后台/Surface专项，记录设备证据。
3. 配置/收藏/历史端到端测试：MMKV配置CRUD/默认切换/完整重启、Repository重复收藏与磁盘故障。随后再定义JS协议fixture及资源限制实验。

长期采用端口/适配器渐进演进，保留现有AVBox与播放器。不要把审计建议当已实现功能，不因CI绿色宣称已解决全部历史问题。
