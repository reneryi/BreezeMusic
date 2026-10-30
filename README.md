# BreezeMusic · 微风音乐

<p align="center">
<strong>耳畔微风起 · 帧享真无损</strong><br>
一款轻量、纯粹、高解析的第三方「酷狗概念版」Android 原生音乐客户端
</p>

<p align="center">
<img src="https://img.shields.io/badge/Version-v1.3.0-00E5FF?style=flat-square" alt="Version" />
<img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3898FF?style=flat-square" alt="Platform" />
<img src="https://img.shields.io/badge/Language-Kotlin%201.9-7F52FF?style=flat-square" alt="Kotlin" />
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20120fps%20Spring%20Motion-00E5FF?style=flat-square" alt="Compose" />
<img src="https://img.shields.io/badge/Audio-Media3%20ExoPlayer%20%2B%2064bit%20DSP-00E5A3?style=flat-square" alt="Audio" />
<img src="https://img.shields.io/badge/Architecture-Pure%20Client--Side%20Direct-FFD54F?style=flat-square" alt="Direct" />
<img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License" />
</p>

## 一、项目简介

**BreezeMusic（微风音乐）** 是基于 **Kotlin + Jetpack Compose (Material 3) + AndroidX Media3** 构建的酷狗概念版第三方原生 Android 客户端。

项目将 [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) 中酷狗概念版（`appid = 3116`、`clientver = 11440`）的全套签名算法、RSA/AES 加解密、风控设备注册（`dfid`）与音源解析协议 **100% 纯 Kotlin 端侧直连化**，不依赖任何 Node.js、Python 或 Go 第三方中转服务器。在此基础上，深度打造了 **单层 96dp 极速双色启动页、Hi-Res 真实流头解析、64-bit 母带级 DSP 调音台、KRC 逐字流光与系统级穿透桌面歌词、全局 120fps 欠阻尼弹性物理动效与方向性运动虚化、全链路低功耗/熄屏零唤醒守护、无界去边框化设置中心、多音质下载与 1000P 高清封面/双语歌词二进制内嵌** 等核心体验。

## 二、核心特性与功能介绍

### 1. 纯端侧直连与概念版 VIP 权益闭环

* **零后端端侧直连**：纯 Kotlin 实现酷狗概念版 `signature`、`signatureTracker`、`register.dev` 设备风控注册与 `/v6/priv_url`、`/v5/url` 专属 `key` 签名算法，所有请求由手机端直连官方网关，内置国内免 VPN 多通道容灾路由。
* **全流程账号与硬件级安全**：支持手机短信验证码登录（自动识别并支持同一手机号下多酷狗账号切换）与二维码扫码登录，登录凭证通过 **Android Keystore (AES-GCM)** 硬件级加密持久化。
* **每日畅听 VIP 自动续期**：支持概念版每日签到领取畅听 VIP、升级 VIP 权益、CSCC 三段式真实听歌时长上报，精简「我的」页 VIP 权益卡片文案（每日签到 · 畅享无损），并集成基于系统 `JobScheduler` 的后台静默签到守护（`VipGuardianJobService`）。
* **完整曲库生态**：覆盖每日推荐、私人 FM、新歌速递（按发行日期与拼音首字母双维排序）、官方排行榜、歌单广场、歌手/专辑详情及四级楼中楼评论区（支持酷狗原生动态表情与评论图片预览）。

### 2. 高解析音源、硬件级 `STREAMINFO` 探测与 64-bit 母带 DSP

* **多音质智能解析与回退**：通过 `/v2/get_res_privilege/lite` 补全 `res_hash`（Hi-Res）、`sq_hash`（SQ 无损 FLAC）、`320_hash`（HQ 高品）与 `ogg_128_hash`（标准），按用户偏好音质自动平滑降级，支持移动网络省流保护与未登录访客态音源解析。
* **FLAC `STREAMINFO` 硬件级真实采样率探测**：遵循 [RFC 9639](https://www.rfc-editor.org/info/rfc9639) 标准通过轻量 HTTP `Range: bytes=0-511` 请求直接解析音频流前 42~512 字节的 **FLAC `STREAMINFO` 块**（以及 OGG Vorbis 识别头、MP3 MPEG 帧头），精准还原 `44.1 kHz / 48.0 kHz / 96.0 kHz / 192.0 kHz` 真实采样率与 `16bit / 24bit` 位深。
* **64-bit/Float 母带级 DSP 引擎**：内置 `ConceptMasteringAudioProcessor` 与 `AudioEffectManager`，采用 64-bit 双精度 Biquad 级联滤波网络，智能对 0dB 平直频段执行整组短路跳过以降低解码计算开销；提供 10 段参量均衡器（PEQ）、蝰蛇声效预设、动态超重低音、人声清澈度、电子管温暖度激励、Schroeder 四梳状二全通立体声混响与等响度补偿。
* **进阶播放控制**：支持高潮副歌速刷（Climax 30 秒精华连播）、KTV 纯伴奏实时切换、跨曲无缝淡入淡出（Crossfade）、ReplayGain 音量平衡，以及 0.5x~2.0x 倍速与升降调独立/联动控制。

### 3. KRC 逐字流光歌词、全局穿透桌面歌词与 2K 海报

* **端侧 KRC 逐字解密引擎**：内置 16 字节密钥循环 XOR 异或 + Zlib Inflate 解压器，毫秒级解析行级与字级时间戳、双语翻译及日/韩罗马音注音（纯中文歌曲自动隐藏译文/音译按钮）。
* **沉浸式舞台歌词渲染**：支持字内卡拉 OK 平滑扫光、基于 $O(1)$ 预判（`hasInterludeCandidateAt`）的前奏/间奏三点流光呼吸波点、连续光学景深高斯模糊、双行预览及点击任意歌词行直接跳转播放（Seek）。
* **系统级跨应用桌面歌词**：支持横向全屏自适应宽度、双行插空居中与长句自然三行换行（容器高度恒定不抖动）、封面动态取色实时随动、物理点击穿透锁定、锁屏熄屏 `ACTION_SCREEN_OFF` 自动挂起 0fps 休眠，以及系统通知栏/OPPO 流体云快捷开关与解锁按钮。
* **2K 超清歌词海报生成**：支持多选歌词段落、切换多款视觉排版模板，一键渲染 2K 分辨率 PNG 海报并保存至系统相册。

### 4. 多音质本地下载与零依赖高清封面/歌词二进制内嵌

* **纯 Kotlin 二进制元数据内嵌器 (`AudioMetadataEmbedder`)**：参考 [lisalee23042605/kgmusicbot](https://github.com/lisalee23042605/kgmusicbot) 并在端侧实现零外部依赖的二进制标签写入：
  * **FLAC (`fLaC`)**：解析并重组 Metadata Block 链表，写入 `Vorbis Comment (Block Type 4)`（包含 `TITLE`、`ARTIST`、`ALBUM`、`ALBUMARTIST`、`DATE`、`COMMENT`、`LYRICS`、`UNSYNCEDLYRICS`）与 `Picture (Block Type 6, type=3 Front Cover)`。
  * **MP3 (`ID3v2.3`)**：构建 Synchsafe 头部与 UTF-16LE (with BOM) 编码的 `TIT2`、`TPE1`、`TPE2`、`TALB`、`TYER`、`TDRC`、`COMM`、`USLT`（内嵌时间轴歌词）及 `APIC`（内嵌高清封面）帧。
  * **OGG (`OggS`)**：自动通过文件前 4 字节魔数识别容器类型，保护 OGG 音频页同步头不被破坏。
* **默认内嵌 1000P 高清封面与歌词 + 可选独立 `.lrc` 文件**：下载时自动将酷狗封面 URL 升级为 `1000×1000` 高清大图内嵌，并支持自定义是否同步在歌词目录生成同名 `.lrc` 文件。
* **自定义保存路径与独立「下载」设置中心**：支持通过系统 SAF 文件夹选择器（`OpenDocumentTree`）或手动输入自定义音乐下载路径与歌词保存路径，并提供独立的「最近下载记录」二级管理页面。

### 5. 单层极速启动页、双主题持久化与无界现代设计

* **单层 96dp 黄金比例启动页**：统一系统级 `Starting Window` 为 208dp 画布内嵌 96dp 圆角水彩卡片图标（`ic_splash_window_icon_light` / `dark`），彻底移除二级 Compose 开屏覆盖层，消灭图标跳变变形与额外驻留等待，冷启动首帧直达主界面。
* **深浅启动主题独立持久化**：针对各大定制系统（ColorOS / HyperOS / OriginOS / OneUI 等）与原生 Android 12+，通过 `Activity.splashScreen.setSplashScreenTheme` 与 `UiModeManager.setApplicationNightMode` 双重持久化「幽邃微蓝（深色）」与「水彩浸润（浅色）」两套启动页主题，确保启动页底色与 App 内部主题 100% 同步，不受系统暗色模式干扰。
* **设置中心去边框化美化**：彻底移除设置页全部生硬 1dp 边框线组件，全新设计无边框色阶胶囊 `ConceptBorderlessChoicePill` 与色阶操作按钮 `ConceptTonalActionButton`；一级菜单采用极简微摘要 + 右侧亮色状态徽章，权限管理与存储缓存卡片支持按需展开折叠。
* **封面 13 级动态取色与横竖屏舞台**：提取当前播放歌曲专辑封面主色构建色相锁定的 13 级明度色阶（严格满足 WCAG 4.5:1 对比度）；结合前台按需启停的倾角传感器支持横放手机平滑切换至横屏黑胶/歌词舞台。

### 6. 全局 120fps 弹性物理动效与方向性运动虚化

* **`ConceptMotion` 五维弹簧体系**：基于播放页队列抽屉丝滑范式提炼五维物理弹簧模型（`coplanarTabSpring` / `spatialPageSpring` / `spatialSheetSpring` / `categoryContentSpring` / `floatingPopupSpring`，阻尼比 $\zeta = 0.75 \sim 0.82$）。
* **同平面 100% 满屏横滑与方向虚化**：底部一级导航「发现 ↔ 我的」升级为与底栏胶囊同向的同平面满屏弹性滑动，并在 Android 12+ 实时叠加 GPU `RenderEffect` 水平方向性动态虚化（`applyDirectionalMotionBlur`），运动停止自动归零离屏开销。
* **通用弹性浮层架构**：全项目 8 大底部抽屉与弹窗（歌曲操作、歌单创建、添加至歌单、VIP 中心、下载对话框等）统一接入零重组 `ConceptAnimatedOverlaySheet` 弹性架构，支持手势下拉阻尼跟手与中断速度继承；分类标题下方内容流支持顺向弹性滑移与左右虚化。

### 7. 全链路低功耗守护、后台减负与 120fps 滑动满帧率

* **前后台自适应进度轮询**：播放器暂停且无切歌解析任务时彻底退出主线程轮询（0 次/秒待机唤醒）；退至后台听歌时将进度轮询从 `120ms` 自动节流至 `200ms`（桌面歌词）/ `400ms`（高潮段或淡入淡出）/ `1000ms`（常规后台），大幅降低耗电发热。
* **锁屏熄屏深度休眠**：监听系统 `ACTION_SCREEN_OFF` 广播在手机锁屏熄屏瞬间立即暂停后台桌面歌词 Ticker 与重绘，消除 `NativeDesktopLyricView.onDraw` 每帧对象分配与跨进程 `WindowManager.updateViewLayout` 冗余 IPC。
* **动效可见性精准门控**：前台状态感知（`rememberIsLifecycleStarted`）与视图可见性门控联动黑胶旋转、硬件加速度计与 120Hz VSYNC 帧循环，后台或隐藏视图时即刻精准休眠；歌词行前奏/间奏引入 $O(1)$ 预判，消除 95% 以上非间奏时段的空跑采样。
* **120fps 垂直滑动零重组与图片降维**：「我的」页滚动偏移因子的读取 100% 移入 `drawBehind` 与 `graphicsLayer` 绘制阶段，滑动全程 0 次整屏重组与 0 次 Layout 重排；构建 `normalizeKugouThumbUrl` 全局缩略图降维管线（自动将 `/400/`、`/1000/` 路径降维至 `150`/`240`，统一 `Precision.INEXACT` 并禁用列表 `crossfade`），升级渐进队列加载步长并在滑动时挂起；评论区增加滑屏停稳防抖以规避并发请求风暴；全量补齐 `LazyColumn` / `LazyRow` 的 `contentType` 与 `key`。
* **多应用混音同播与耳机线控**：支持三档音频焦点模式（独占焦点 / 压低音量闪避同播 Ducking / 完全混音同播），提供有线耳机切歌定制、连接耳机自动续播及蓝牙歌词延迟自动补偿。
* **最小化透明权限管控**：在「设置 -> 权限管理」中完整公开全部 8 项权限的获取用途、系统权限标识、隐私承诺与关闭影响，支持随时独立开关。

## 三、技术栈概览

| 领域 | 采用技术与方案 |
| :--- | :--- |
| **开发语言与并发** | Kotlin 1.9+ · Kotlin Coroutines & StateFlow / SharedFlow · 前后台自适应节流 |
| **声明式 UI 与物理动效** | Jetpack Compose (BOM 2024.06) · Material 3 · `ConceptMotion` 欠阻尼弹簧体系 · `RenderEffect` 方向性运动虚化 · 120Hz 零重组绘制管线 |
| **音频播放与会话** | AndroidX Media3 (`ExoPlayer` · `MediaSessionService` · `SimpleCache` 边听边存 · 120ms~360ms 低延迟 PCM 缓冲) |
| **母带级音频 DSP** | 自研 64-bit Float PCM `ConceptMasteringAudioProcessor` (Biquad PEQ / Schroeder 混响 / 动态低音 / 0dB 平直频段短路) |
| **网络与协议签名** | OkHttp 4.12 · Retrofit 2.11 · Gson · 纯端侧 `KugouSigner` (MD5 / AES / RSA / Zlib · 免 VPN 多路容灾) |
| **音频标签二进制引擎** | 自研零依赖 `AudioMetadataEmbedder` (FLAC `STREAMINFO`/Vorbis/Picture & MP3 `ID3v2.3` APIC/USLT) |
| **图片加载与色彩提取** | Coil Compose 2.6 (`normalizeKugouThumbUrl` 缩略图降维 + L1 预热对齐) · 自研 `CoverTonalPalette` 13 级色阶算法 |
| **安全与本地持久化** | Android Keystore (AES-GCM 硬件级加密) · SharedPreferences · SWR 歌单秒开快照缓存 |

## 四、项目目录结构

```
BreezeMusic/
├── app/
│   ├── build.gradle.kts                         # 模块级 Gradle 构建配置 (versionName = "1.3.0", versionCode = 6)
│   ├── proguard-rules.pro                       # Release 混淆与 R8 压缩保留规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml              # 应用组件、前台媒体服务、桌面小组件与权限声明
│       │   ├── java/com/concept/music/
│       │   │   ├── ConceptMusicApp.kt           # Application 入口：全局单例按需初始化与 Coil 图像管线配置
│       │   │   ├── MainActivity.kt              # 宿主 Activity：双套启动页持久化、120Hz 高刷申请与同平面导航宿主
│       │   │   ├── data/
│       │   │   │   ├── kugou/                   # 酷狗概念版 (appid=3116) 纯端侧协议层
│       │   │   │   │   ├── KugouSigner.kt       # 核心签名算法、RSA/AES 加解密、KRC 解密与设备 ID 生成
│       │   │   │   │   ├── KugouTransport.kt    # OkHttp 统一网关传输层、免 VPN 容灾路由与自动重试
│       │   │   │   │   ├── AuthRepository.kt    # 短信验证码登录、扫码登录、dfid 注册与 Token 续期
│       │   │   │   │   ├── AccountRepository.kt # Android Keystore 加密存储与多账号无缝切换管理
│       │   │   │   │   ├── MusicRepository.kt   # 音源解析 (/v6/priv_url & /v5/url)、曲库搜索、推荐与歌单接口
│       │   │   │   │   ├── LyricRepository.kt   # KRC 逐字歌词/双语译文/罗马音解析与标准 .lrc 导出格式化
│       │   │   │   │   └── VipRepository.kt     # 概念版每日签到领 VIP、升级 VIP 与 CSCC 听歌时长上报
│       │   │   │   ├── model/                   # 领域数据模型层 (Song / Lyric / Playlist / Comment / VipStatus)
│       │   │   │   └── vip/
│       │   │   │       └── VipManager.kt        # 全局曲库、歌词、账号与 VIP 仓库门面 (Facade)
│       │   │   ├── service/                     # 后台服务、DSP 音频引擎与系统级能力管理
│       │   │   │   ├── PlaybackService.kt       # Media3 前台播放服务、64-bit DSP 处理器、熄屏零唤醒与流体云通知
│       │   │   │   ├── PlaybackCoordinator.kt   # 播放队列调度、无缝预热、高潮试听与跨曲淡入淡出控制
│       │   │   │   ├── AudioEffectManager.kt    # 10 段参量均衡器、声场调音与蝰蛇音效状态管理
│       │   │   │   ├── AudioCacheManager.kt     # Media3 SimpleCache 流媒体缓存与四维分项缓存统计/清理
│       │   │   │   ├── AudioMetadataEmbedder.kt # 零依赖 FLAC/MP3 标签、1000P 高清封面与歌词内嵌 + STREAMINFO 解析
│       │   │   │   ├── MusicDownloadManager.kt  # 多音质下载队列、自定义音乐/歌词路径与独立 .lrc 导出管理
│       │   │   │   ├── SystemLyricBridge.kt     # 跨应用全局悬浮桌面歌词窗口管理、零分配渲染与穿透锁定
│       │   │   │   ├── AppPermissionManager.kt  # 8 大权限公开透明说明、系统授权跳转与独立开关管控
│       │   │   │   ├── VipGuardianManager.kt    # JobScheduler 每日静默自动领取概念版 VIP 后台任务
│       │   │   │   └── ConceptMusicWidgetUpdater.kt # 桌面音乐小组件状态同步与远程视图渲染
│       │   │   └── ui/                          # Jetpack Compose 声明式界面层
│       │   │       ├── account/                 # 登录页 (验证码/扫码)、账号切换与安全验证弹窗
│       │   │       ├── comment/                 # 四级楼中楼评论区（含滑动防抖加载）、热评/最新切换与大图查看器
│       │   │       ├── components/              # 核心复用组件库 (NowPlayingSheet / SongItemRow / VipBannerCard 等)
│       │   │       ├── home/                    # 发现页、每日推荐、私人 FM、新歌速递、排行榜与场景电台
│       │   │       ├── library/                 # 我的音乐库（绘制期联动顶栏）、歌单详情、歌手主页与专辑详情页
│       │   │       ├── search/                  # 搜索页、热搜榜、历史记录与多维综合搜索结果页
│       │   │       ├── settings/                # 无边框色阶化二级设置中心 (SettingsScreen.kt) 与可折叠更新日志
│       │   │       ├── theme/                   # 深浅双主题、封面 13 级动态取色与 ConceptMotion 弹性动效引擎 (MotionTokens.kt)
│       │   │       └── viewmodel/               # 播放器（含前后台自适应节流）、发现页、音乐库与搜索 ViewModel
│       │   └── res/                             # Android 资源目录（含 96dp 深浅双套启动页图标与样式）
│       └── test/java/com/concept/music/         # 单元测试套件 (UiAlignmentRegressionTest / SongPlaybackSwitchTest 等)
├── build.gradle.kts                             # 根项目构建脚本
├── settings.gradle.kts                          # Gradle 仓库与模块配置
└── README.md                                    # 项目说明文档
```

## 五、快速开始与本地构建打包

### 1. 环境要求

* **JDK 版本**：JDK 17（Android Studio Ladybug / Koala / Iguana 内置 JBR 17 即可）
* **Android SDK**：`minSdk = 26` (Android 8.0)，`compileSdk = 34` (Android 14)
* **Gradle**：项目自带 Gradle Wrapper (`./gradlew` / `gradlew.bat`)，无需手动安装

### 2. 编译手机版 (Phone) 与平板版 (Tablet) 安装包（双签名独立区分）

项目内置 `phone`（手机端，`phoneSign` 签名）与 `tablet`（平板端，`tabletSign` 签名）双构建变体：

```bash
# 编译手机版与平板版 Debug 安装包
./gradlew :app:assemblePhoneDebug :app:assembleTabletDebug

# 编译手机版与平板版 Release 正式安装包
./gradlew :app:assemblePhoneRelease :app:assembleTabletRelease
```

编译完成后，生成的双平台独立签名安装包路径分别为：
- 手机版：`app/build/outputs/apk/phone/release/app-phone-release.apk`
- 平板版：`app/build/outputs/apk/tablet/release/app-tablet-release.apk`

### 3. 运行单元测试套件

```bash
# macOS / Linux
./gradlew :app:testDebugUnitTest

# Windows PowerShell / CMD
.\gradlew.bat :app:testDebugUnitTest
```

### 4. 编译 Release 正式版安装包

如需编译启用 R8 代码优化与资源压缩的正式版 APK，可在 Android Studio 中选择 **Build -> Generate Signed App Bundle / APK...**，或在命令行执行：

```bash
./gradlew :app:assembleRelease
```

## 六、功能使用说明与小贴士

1. **如何开启「音乐下载与歌词保存」功能？**
   * 为保持界面纯粹合规，项目的音乐下载功能**默认处于关闭与隐藏状态**（默认状态下设置页与歌曲菜单均不显示下载入口）。
   * 如需使用，请前往 **「设置 → 权限管理」**，找到 **「音乐下载与歌词保存」** 卡片并手动打开开关；此时返回设置页即可看到独立的 **「下载」** 设置项，同时歌曲右侧 `⋮` 更多菜单与播放页更多面板中也会解锁 **「下载歌曲」** 按钮。

2. **自定义下载路径与歌词独立保存**：
   * 在 **「设置 → 下载」** 中，可点击 **「存储权限设置」** 跳转系统授权页面开启存储权限，并通过「选择文件夹」或「手动输入」分别指定音乐保存目录（默认 `/storage/emulated/0/Music/BreezeMusic`）与 `.lrc` 歌词目录。
   * 下载歌曲时，**1000×1000 高清专辑封面、歌曲完整标签与时间轴歌词默认直接内嵌到 `.flac` / `.mp3` 音频文件中**；若同时需要独立的 `.lrc` 歌词文件，打开「独立保存 .lrc 歌词文件」开关即可。

## 七、免责与版权声明

1. **非商业学习性质**：本项目（**BreezeMusic / 微风音乐**）系完全开源、非营利性质的个人计算机软件工程与移动开发技术学习项目，旨在探索 Kotlin 协程并发、Jetpack Compose 声明式 UI、AndroidX Media3 实时音频数字信号处理（DSP）及网络协议架构设计。本项目仅供个人学习、技术交流与编程研究使用，严禁用于任何商业牟利、付费售卖或违规分发。
2. **音乐版权归属**：本应用采用纯端侧直连架构，自身不存储、不托管任何音乐音频、歌词或封面文件。应用内展示及播放的所有内容均来自酷狗音乐官方公开服务器，其著作权与邻接权均归原版权方及唱片公司所有。请支持正版音乐，下载的测试文件请于 24 小时内删除。

## 八、开源致谢

* [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi)：提供了详实的酷狗音乐与酷狗概念版协议研究基础。
* [lisalee23042605/kgmusicbot](https://github.com/lisalee23042605/kgmusicbot)：提供了酷狗概念版多音质下载与封面/歌词内嵌的实现参考。
* [AndroidX Media3](https://developer.android.com/media/media3) & [Jetpack Compose](https://developer.android.com/jetpack/compose)：提供了现代化的 Android 媒体播放与声明式 UI 框架支持。

## 九、开源许可证 (License)

本项目代码基于 [GNU General Public License v3.0 (GPL-3.0)](LICENSE) 协议开源。任何人均可自由学习、修改与分享，但任何衍生版本必须保持相同的开源协议且禁止商业化闭源牟利。
