# 2026-11-10 原生 TV 首版改造

用户反馈点开闪退后新增 TV 主题与真实 Leanback 列表膨胀/绑定的 Robolectric 回归测试。旧版实际复现 `InflateException`，根因为主题缺少 `browsePaddingBottom` 等 Leanback 尺寸属性，`VerticalGridView` 构造时无法解析 TypedArray 中的尺寸。补齐浏览边距与品牌色属性后，同一启动测试通过。修复版为 `1.3.2-TV-NativePreview2-Fix1` / 41；这不是手机硬件或 ABI 原因的确认，也不替代用户手机上的冷启动复测。

参考 APK 已通过 Androguard 和 Apktool 实际解析：包名 `com.fongmi.android.tv`，版本 `5.5.6-20261030`，启动 `HomeActivity`，sensorLandscape，armeabi-v7a。首页使用 Leanback 的 VerticalGridView、HorizontalGridView、ArrayObjectAdapter、ListRow 与 Presenter；`activity_home_v2.xml` 含 heroBg、heroMetaText、heroDescText 和水平/竖直遮罩。焦点选择器区分 focused、selected 与普通状态。

旧 TV 首页存在空焦点回调、无点击分类、未接线的历史收藏导航以及依赖手机布局偏好的加载策略。新入口为 `src/tv` 下的原生 Leanback Activity，旧 TvMainScreen 已删除。BaseActivity 统一 TV 横屏策略，HomeViewModel 将 TV 视为独立横向数据请求策略；无默认源时立即退出加载态。pyramid 同步选择 ARM32，避免保留 arm64 Python 依赖。

本机首次构建失败于缺少 Python 3.10；嵌入式 Python 重试失败于缺少 venv。已准备完整便携 Python 后继续构建。构建与 APK 检查的最终状态应以交付日志为准。

最终 `:app:assembleDebug -PCATTV_TV_BUILD=true` 构建成功；`:app:testDebugUnitTest` 在补齐 Robolectric 运行库后成功，192 个测试类、1443 个用例、0 失败、0 错误、3 跳过。最终包版本 `1.3.2-TV-NativePreview2` / 40，包名 `com.github.avbox.osc.tv`，原生入口 `TvHomeActivity`，landscape，15 个原生库全部通过 ELF32 / ARM 校验，APK v2 签名通过。尚未进行 TV 真机运行验收。

Version 44: reference smali jr3.t uses w500 posters; uh3.P replaces w500 with w1920 for backdrops; HeroImageView targets 1920x1080. TV loader follows these separate sizes, falls back to original when w1920 fails, normalizes duplicate size suffixes, and falls back to source artwork when TMDB lookup fails. Existing Coil source-header support remains. No device runtime image quality check was available.

Version 45: reference-layout update. Native poster cards increased from 138x198 to 196x294 design units, transparent card/category surfaces, larger rails, simplified top navigation. Hero gradients follow decoded bg_hero_mask_h/v (black to transparent at midpoint). Added TMDB transparent title artwork with text fallback and cancellation-safe selection handling. Tests cover logo-to-text selection changes and missing-backdrop fallback. No TV device visual verification; settings and QR work remain incomplete. Local .tools gate scripts are absent from this branch archive.
