# 基础质量体系与平台边界

## 仓库核对（2026-11-20）
main 为 66a83ca；基础 CI PR #14 尚未合并，本次沿用同一独立分支，避免重复 PR。
正式 Release 最近包括手机 v1.3.6、v1.3.5，TV tv-arm32-v70。
main 的 Android 版本声明与发布分支不同，不调整版本或签名。
现有开发分支包括 cattv-branding、release/mobile-v1.3.4、fix/mobile-panconfig-lifecycle、fix/pip-fongmi-style 及多个 TV 分支和 Dependabot 分支。
手机为 Gradle app/quickjs/pyramid/libs:backdrop；TV v66 使用已有 APK + apktool + Java 补丁构建脚本，不是同一个 Gradle variant。
本 CI 的 TV 验证对象固定为 tv-v66 的 7f70b5f，不代表最新 v70 正式 APK 的回归认证。
发布工作流仍检出 cattv-branding，以 secrets 恢复临时签名文件，保留原版本参数和手动发布入口。测试失败仅阻止发布，手动 APK 产物仍可下载。

## 自动化覆盖与边界
复用 JUnit 4、Robolectric、Room 3、BundledSQLiteDriver，不新增依赖。
ConfigParserTest 覆盖正常、多源、字段映射、缺失字段跳过、空和损坏集合 JSON；不改变已有容错行为。缺失字段当前仅日志提示，不额外制造 UI 提示。
DatabasePersistenceTest 用真实文件数据库覆盖收藏写入/读取/同主键替换/删除、历史集数和线路更新、touch、订阅隔离及关闭重开。
同主键替换不等于验证收藏 Repository 的重复添加防护；数据库重开不等于进程重启。
已有 PlaybackProgressTest、WatchProgressRulesTest 验证进度编码和规则；HttpTest/HttpPolicyTest/RetryPolicyHttpStatusTest 等是网络与恢复测试基础。
原生 MMKV 配置增删改、默认切换、完整进程重启恢复、数据库异常上层提示尚须补真机或设备测试；不能用内存 Map 冒充生产持久化。
ConfigRepository、RoomHistoryRepository 的部分异常处理依赖全局 KV/AppLog，不为测试改业务。DAO 读异常发生在 getVodInfo 的 try 之前，不能宣称所有数据库错误都被吞掉。
不自动访问真实影视源，不提交接口密钥。

## CI 报告与静态检查
push/PR 编译 Kotlin/资源，运行全部单测及 lintDebug。Actions Summary 显示测试计数、失败名、Lint 问题和耗时；XML/HTML 随 artifact 上传，完整堆栈在日志。
现有 Gradle lint.abortOnError=false 保持不变，Lint 发现会报告，尚不作为零存量告警硬门禁。Kotlin 编译和单测失败仍使检查失败。
Lint 检查空指针、资源引用及 Android API 等；不引入自动删依赖工具，动态爬虫依赖不能按静态引用随意删除。
异常处理和重复依赖仍需代码审查，不能声称 Lint 能证明它们全部正确。
当前没有自动正式发布触发器；两个手动发布入口增加同次源码测试门禁，未触发实际发布或覆盖正式 Release。

## 播放器真机检查单
以下全部待 Android 手机和 TV 真机验证，Robolectric 通过不替代设备结论。
记录设备型号、Android 版本、APK SHA、操作步骤、日志、预期与实际结果：
- 全屏进入/退出，旋转后播放位置和布局状态。
- 画中画进入/退出与返回页面；支持与不支持 PiP 的设备。
- Activity 重建、页面退出、进程回收后恢复。
- 前后台切换、锁屏播放、音频焦点与通知控制。
- 网络超时/断网/不可访问地址后恢复，切换线路与集数。
- 页面销毁及连续重复打开，确认资源释放和后台播放策略。
- 配置 CRUD/默认切换、损坏文件、存储权限拒绝；重启后配置/收藏/进度恢复。
- 数据库空间不足/不可写/损坏；确认实际错误表现，记录缺陷再单独修复。

## 未来平台复用分析
| 模块 | 可复用部分 | Android 边界 |
| --- | --- | --- |
| 影视源规范 | 脱敏 JSON fixture、字段语义、兼容性用例 | clan/file/content URI、权限及动态爬虫 |
| 解析 | 纯 JSON 转模型规则可移植 | ConfigParser 混用 UriUtil/AppLog，不能直接当纯跨平台库 |
| 搜索/分类 | 数据字段、排序和过滤语义 | 网络运行时、ViewModel、爬虫宿主 |
| 收藏/历史 | 实体字段、订阅隔离、去重契约 | Room schema、MMKV 编码与迁移 |
| 播放器接口 | 状态/命令概念与测试场景 | Media3、Activity/Service、PiP、音频焦点和 JNI |
| 用户配置 | 配置规范、默认值和序列化兼容 fixture | KV/MMKV 原生库、Context、文件权限 |
下一阶段先定义稳定数据契约与共享 fixture，再评估各端实现；本次不抽公共模块、不开发 iOS/Windows、不替换 AVBox。
