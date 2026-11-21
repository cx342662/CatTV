# 配置兼容性逐项范围

对照 `api/ConfigParser.kt`、`ApiConfig.kt` 与 `sourcedata` 下各 Loader。配置原文完整保存，不等于所有字段已在 iOS 执行；未列为支持的扩展不能推定可用。

| 字段/机制 | iOS 当前实现 | 仍有的差异 |
| --- | --- | --- |
| sites 的 key/name/api | key、name、api 清理首尾空白；缺省 name 使用 key | 缺失 key/type/api 会明确报错，尚未完全复刻 Android 跳过单条坏站点的容错策略 |
| type | 接受整数或整数文本，0/1/4 使用对应 HTTP 路径 | 3 不会伪装成 HTTP 媒体 URL；其他未支持类型报错 |
| header | 对象内的标量转文本，过滤不可发送字符；非对象 header 忽略 | 底层网络栈为 URLSession，不继承 Android 客户端的所有行为 |
| ext | 保留 JSON 原值，type=4 发送 extend、筛选 base64 与长参数 POST | 不能据此执行 JS/JAR/Python 宿主扩展 |
| searchable/timeout | 搜索参与和请求超时 | quickSearch、changeable、filterable 等独立偏好尚未逐项等效 |
| playUrl、root parses | 网页与 JSON 解析、手动选择、cat_ext/扩展头、并发 JSON 尝试 | type=2/3 聚合、全局默认解析器偏好及自定义站点点击规则未完成 |
| spider / sites[].jar | 原文保留 | Android JAR/Dex、反射绑定、QuickJS/Python 桥不在当前 iOS 包中 |
| categories/hide/indexs/style/icon | 原文保留 | 分类覆盖、隐藏站点、展示模式及独立站点图标策略未完整移植 |
| click、danmaku | 原文保留 | 自定义点击脚本和弹幕业务未移植；媒体元素发现不等于实现这些字段 |
| root urls 配置仓库/聚合入口 | 可分别导入实际子配置 URL | 只含 urls 的仓库目录没有实现原 Android 的自动展开/选择界面 |
| root hosts/proxy | 原文保留 | 不执行 Android hosts 映射、代理规则或继承其 DoH/网络客户端；依赖这些能力的源可能不可用 |
| lives | 内联频道、M3U/TXT、分组/线路及播放头 | 直播回看/catchup、部分特殊代理 URL、完整 EPG 名称映射库未移植 |
| EPG | 用户地址或直播源 epg，XMLTV/JSON、日期/时区/午夜处理 | 不内置原 Android 的默认第三方节目单地址；不提供未授权的资源服务 |
| 二维码 | 配置 URL 生成/分享/扫码 | 不等同于 Android 的局域网配置推送/Web 管理服务 |

## 运行时差异

JAR 依赖 Android/JVM 的类加载、Context、Handler/Looper 与宿主类型签名，iOS 不能直接链接这些 ABI。JsLoader 还允许加载自定义 JS API 的 Dex 类，JsSpider 再把模块与原生函数绑定进 QuickJS；替换成 WKWebView 并不能继承这些桥。Python 的 Chaquopy 和 Java 桥同样需要独立原生移植。

当前替代边界是 HTTP type=4 契约：用户自控的兼容服务可返回目录/详情/播放结果。但本项目没有交付一个已经跑通全部爬虫的服务，不能把该替代接口写成已完成的运行时。

## 记录身份与迁移

源 key 规范化后，早期保存的站点选择在读取时同步去掉首尾空白。历史/追剧以保存的 source 快照重算身份，不持久化旧计算 ID；因此原文不需强行改写。不同 API/ext 的同 key 站点使用不同记录标识。

## 验收边界

核心测试覆盖格式归一化、请求参数、解析与错误分支；受控 UI 测试验证真实网络请求到渲染/播放。尚无用户实际配置的完整源兼容清单，不得从测试数量推断这些未实现字段已经可用。详细播放器缺口见 PLAYER_PARITY.md。
