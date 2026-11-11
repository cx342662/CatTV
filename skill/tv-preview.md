# CatTV 原生 TV 预览版

版本 43 根据用户要求，将没有 TMDB 横版图时的竖版海报也铺为整屏背景，取消右上角竖版卡片兜底；先显示对应海报，再异步更新为可用横版图。新增红/蓝两部影片切换且 TMDB 无图片的整屏背景回归测试。顶部菜单使用透明文字与焦点下划线；设置主页使用原生双列焦点卡片，提供常用播放、弹幕、历史、缓冲、搜索和原生 TMDB 编辑。未覆盖的高级项经“更多设置”访问原页面，不能视为全部设置子页面均已 TV 化。

HomePreview3 的首页按参考 `activity_home_v2.xml` 调整：全屏横版背景叠加左右和底部渐变，左侧叠放标题/评分/简介，分类独立于海报轨道并固定于底部区域，海报恢复 2:3。缺少横版背景时展示独立竖版海报，不将竖图裁成整屏背景。所有 TV 尺寸按 1280×720 设计画布缩放，避免高密度手机横屏时固定 dp 占满整个窗口。切换焦点保留原背景，待新图成功后淡入。没有设备截图或真机视觉验收，不宣称视觉效果已完全复刻。

TV 构建以 `CATTV_TV_BUILD=true` 选择 `src/tv/java` 与 Leanback 依赖，并在 Manifest 直接启动 `TvHomeActivity`。手机版 MainScreen 不再承担 TV 入口。TV 页面沿用 HomeViewModel、ConfigManageViewModel、HistoryViewModel、CollectViewModel、配置存储及现有播放分发。

首页使用 RowsSupportFragment、ListRowPresenter、ArrayObjectAdapter 和独立卡片 Presenter。分类、更多、历史、收藏与站点选择均在原生 TV 界面处理；源管理使用遥控器可操作的平台对话框。海报焦点缩放由 Leanback 提供，导航焦点以白色边框和蓝灰底色显示。焦点选片驱动 TMDB 横版背景、评分与简介，图片错误只影响增强内容。

BaseActivity 在 TV 构建中统一返回 landscape，不受最小屏宽的手机竖屏规则覆盖。HomeViewModel 在 TV 构建中独立请求推荐和全部分类，不读取手机纵向布局偏好。app 与 pyramid 都选择 armeabi-v7a。

首版范围：原生首页、分类分页、源切换、接口增删改、搜索输入和分站点结果轨道、历史和收藏入口、长按收藏菜单。详情播放、直播、完整 TMDB 设置暂复用现有 Activity；这些页面尚不能视为已完成原生 TV 改造。未经 APK 构建和运行验证，不声明首版已验收。
