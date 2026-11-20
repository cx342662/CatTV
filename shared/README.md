# 跨平台协议边界

配置兼容以 Android ConfigParser / SourceResultParser / SpiderLoader 的实际行为为准。iOS Codable 模型当前仅为起步子集，不是最终跨平台协议。保留源 key、type、api 和根 spider；不将爬虫入口误认为媒体 URL。后续补充扩展字段、headers、相对路径、解析器、过滤器和持久化版本迁移后再冻结规范。
