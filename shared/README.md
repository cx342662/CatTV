# 跨平台配置与播放边界（草案 v0.1）

本目录用于 iOS 和未来 Windows 实现之间的文档/格式复用；不改变 Android 的解析器、数据文件或包名。Android 的 ConfigParser、SourceResultParser、SpiderLoader 仍是兼容性对照依据。

## 配置原文与能力分离

平台应保存用户导入的原始 JSON 字节、文档标识、用户名称和可选来源 URL；编辑前校验成功后原子替换。未知字段保留在原文中，不能因为某平台暂不支持就删除。配置能够导入，不代表其中每一种源都能在当前平台运行。

| 配置字段 | 当前 iOS 消费方式 | 兼容性边界 |
| --- | --- | --- |
| sites[].key/name/type/api | 站点标识、界面、请求路由 | type 支持数值或整数文本；源 key 在单份配置内唯一 |
| sites[].header | URLSession 请求头 | 名称和值需通过 HeaderPolicy，控制字符不能发送 |
| sites[].ext | 扩展 API 的 extend / base64 筛选请求 | 字符串或 JSON 原值；不是通用爬虫桥 |
| sites[].playUrl | 媒体/网页播放解析前缀 | 实际媒体结果可带请求头 |
| sites[].timeout/searchable | 请求超时与搜索参与 | 爬虫专用扩展语义不能据此宣称支持 |
| spider | 原文保留 | 当前 iOS 不运行 Android JAR |
| parses | type=0 网页、type=1 JSON、cat_ext/扩展请求头 | type=2/3 未实现 |
| lives | 内联 channels、远程 M3U/TXT、请求头、epg | HTTP(S) AVPlayer 路径；额外协议与回看未实现 |

站点 HTTP API、解析器及列表的相对路径以配置来源 URL 为基准；本地导入无来源地址时不能猜测远程基址。用户文档只保存在各平台自己的应用数据目录，不能覆盖 Android 数据。

## 平台源适配器契约

适配器按站点执行 home/categories、category+filters+page、search+page、detail+id、play+line+episode。返回内容不依赖 SwiftUI/Compose/Windows 视图：

- 目录响应：分类 id/name、筛选 key/name/value、视频数组、当前页、总页。
- 视频：稳定 id、名称、海报、备注、简介、年份/地区/语言/类型/演职员、线路和选集。
- 播放响应：实际 URL、必要请求头、是否仍需网页解析。URL 是播放结果，不是原始 JAR/JS/Python 文件路径。
- 失败：无效配置、未实现的源类型、HTTP 状态、网络中断、解析失败、播放器不支持的编码/协议，分别向用户报告。

iOS 的 CatTVCore 已提供这些领域类型和 HTTP 实现。未来 Windows 可以按契约另写实现；此目录没有冒充一套已运行的 Windows 内核或爬虫兼容服务。

## 源与记录标识

key 仅在同一配置中唯一。历史/追剧以 key、源 type、API、ext 的规范化数组计算 SHA256，再与视频 ID 组成记录标识，避免不同源共用 key 和 ID 时覆盖。显示名称不影响身份；API/扩展参数变更视为不同源。请求头不加入身份，便于凭据更新后继续使用同一记录。原始 source 快照随记录保存，早期记录可从快照重算而无需改写已有 JSON。

## EPG

节目单地址由用户设置或直播源 epg 字段提供，不内置第三方服务。支持 {name}/{date} 模板、XMLTV 文件和 JSON API 的 ch/date 查询。对照 Android，JSON 时间及节目日期默认 GMT+8；XMLTV 显式时区优先。XML 外部实体禁用。缓存按完整请求 URL 隔离、短时保存；没有提供回看播放接口。

## 隔离与凭据

影视配置、账号、Cookie、证书或私钥不提交到共享目录。运行时媒体代理使用临时不透明路由；Authorization/Cookie 不转发到与原始媒体不同的主机。Windows 将来应明确平台的代理、后台、解码与签名能力，不能照搬 Android 或 iOS 的可用状态。
