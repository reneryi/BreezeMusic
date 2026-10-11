# 酷狗概念版客户端

Android 客户端使用 Kotlin、Jetpack Compose 和 AndroidX Media3。账号、会员、在线播放与歌词协议深度移植自 [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) 并参考 [kgka_Music_hl](https://github.com/umr-xiaomai/kgka_Music_hl) 与 [md3Music](https://github.com/zzyoxml/md3Music)。Android 应用**全量直连酷狗官方接口，不依赖任何独立的 Node.js 或 Rust API 后端服务**。

## 功能特性

1. **账号与概念版 VIP 权益**
   - 手机短信验证码登录（支持同一手机号多酷狗账号选择）与酷狗 App 扫码登录。
   - 凭证加密存储于 Android Keystore，支持多账号无缝切换。
   - 概念版每日 1 天 VIP 权益与升级权益一键领取及启动自动复核。
2. **在线高音质听歌**
   - 直连概念版 VIP 专属音频接口 `/v6/priv_url`（带 `vip_token` 与设备动态加签），优先获取 FLAC 无损（SQ）与 320K 高品音质。
   - 自动平滑降级机制：无版权或未生效时回退至 `/v5/url`（320K -> 128K）。
   - AndroidX Media3 前台播放服务，支持通知栏控制器、锁屏播放、音频焦点管理与耳机线控。
3. **KRC 逐字歌词引擎**
   - 纯 Kotlin 端侧实现酷狗专属 KRC 歌词解密（16 字节密钥循环 XOR 异或 + zlib inflate 解压）。
   - 提取行级与逐字时间戳，支持毫秒级高频同步渲染与点击歌词跳转播放。
   - 兼容纯 LRC 歌词自动降级解析与内存 LRU 高速缓存。
4. **沉浸式现代播放器 UI (Jetpack Compose)**
   - 全屏沉浸式播放面板：
     - **黑胶唱片视图（默认）**：仿真同心圆黑胶唱盘质感与平滑旋转动效（播放时匀速转动，暂停时驻留原角度）。
     - **逐字歌词视图**：大字体高光动态滚动，支持点击任意歌词直接 Seek 跳转。
     - **专业控制台**：循环模式切换（列表循环 / 单曲循环 / 随机播放）、拖拽进度条、当前播放队列抽屉管理、音质偏好面板切换（无损 SQ / 高品 320K / 标准 128K）。
   - 悬浮 Mini 播放条：微小常驻底部，带封面缩略、标题、歌手、播放控制与极细进度条，单击平滑呼出全屏播放器。
5. **每日推荐（日推）**
   - 直连 `/everyday_song_recommend` 智能推荐，支持一键“播放全部”装载整张日推队列起播。
   - 列表展示序号、SQ 无损标识、VIP 徽标与副标题。
6. **全网音乐搜索**
   - 直连酷狗搜索接口，提供热门搜索标签流式布局。
   - 实时输入联想建议（Search Suggestion）与单曲搜索结果即点即播。

## 本地构建与运行

需要 Android SDK（API 26+，编译 SDK 34）。

构建 Debug APK：

```powershell
./gradlew.bat :app:assembleDebug
```

生成 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`。

运行完整测试与代码静态检查：

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
