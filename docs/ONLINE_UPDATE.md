# CatTV 在线更新发布说明

CatTV「我的 → 在线更新」仅信任本仓库 GitHub Releases 的正式版本，并在安装前核对 APK 的 SHA-256、包名、签名证书和递增的 versionCode。每次安装都需要用户在 Android 系统安装界面确认。

## 首次配置（仅一次）

1. **重新生成一个私有签名密钥，不要复用曾经提交进公开 Git 仓库的密钥。** 历史版本曾经存在仓库签名资料；即使从当前分支删除，Git 历史里仍可能保留，不能把它当成安全的发布密钥。
2. 将新密钥和密码保存在你自己控制的安全位置，并在 GitHub Settings → Secrets and variables → Actions 添加以下秘密：
   - `CATTV_KEYSTORE_BASE64`：整个 .jks 文件的 Base64 内容
   - `CATTV_KEY_ALIAS`
   - `CATTV_STORE_PASSWORD`
   - `CATTV_KEY_PASSWORD`
3. 保证长期使用同一套密钥。密钥丢失或更换后，旧版通常无法直接覆盖安装。
4. 在 Actions → **Publish CatTV Signed Release** → Run workflow 选择 `main`，填写 `versionName`（如 `1.3.1`）和递增的 `versionCode`（首次不低于 `35`），按流程构建和发布。
5. 等待 GitHub Releases 出现正式 CatTV APK（非 Draft / Prerelease）。应用内便可以发现版本并提供下载/安装确认。

## 必须知道

- 本项目此前通过 GitHub Actions 安装的 APK 是 **Debug 签名**。正式签名与原 Debug 签名通常不同，首次切换正式版需要先备份订阅源与配置，再卸载 Debug 版安装正式版。**以后均使用同一套正式签名发布，才能在线覆盖更新。**
- Android 不允许应用在未经用户确认时静默安装更新；「在线更新」可完成检查、下载与验证，安装最后一步必须由用户在系统界面确认。
- APK 必须发布在 `https://github.com/cx342662/CatTV/releases`，对应的 GitHub Release asset 需要带 `sha256:...` 摘要。若摘要缺失，App 会拒绝自动安装。
- 如果 GitHub 在当前网络不可访问，检查或下载也会失败。此功能没有内置大陆镜像或自动代理。
- 请不要向任何人发送 .jks、签名密码，也不要将其写入代码或公开仓库。
