# BreezeMusic · 微风音乐

<p align="center">
<strong>耳畔微风起 · 帧享真无损</strong><br>
一款轻量、纯粹、高解析的第三方「酷狗概念版」Android 原生音乐客户端
</p>

<p align="center">
<img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3898FF?style=flat-square" alt="Platform" />
<img src="https://img.shields.io/badge/Language-Kotlin%201.9-7F52FF?style=flat-square" alt="Kotlin" />
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-00E5FF?style=flat-square" alt="Compose" />
<img src="https://img.shields.io/badge/Audio-Media3%20ExoPlayer%20%2B%2064bit%20DSP-00E5A3?style=flat-square" alt="Audio" />
<img src="https://img.shields.io/badge/Architecture-Pure%20Client--Side%20Direct-FFD54F?style=flat-square" alt="Direct" />
<img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License" />
</p>

## 一、项目简介

**BreezeMusic（微风音乐）** 是基于 **Kotlin + Jetpack Compose (Material 3) + AndroidX Media3** 构建的酷狗概念版第三方原生 Android 客户端。

项目将 [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) 中酷狗概念版（`appid = 3116`、`clientver = 11440`）的全套签名算法、RSA/AES 加解密、风控设备注册（`dfid`）与音源解析协议**100% 纯 Kotlin 端侧直连化**，不依赖任何 Node.js、Python 或 Go 第三方中转服务器。在此基础上，深度打造了 **Hi-Res 真实流头解析、64-bit 母带级 DSP 调音台、KRC 逐字流光与系统级穿透桌面歌词、多音质下载与 1000P 高清封面/双语歌词二进制内嵌、封面色相锁定 13 级动态取色、多应用混音同播与透明权限管控** 等核心体验。

## 二、核心特性亮点

### 1. 纯端侧直连与概念版 VIP 权益闭环

* **零后端端侧直连**：纯 Kotlin 实现酷狗概念版 `signature`、`signatureTracker`、`register.dev` 设备风控注册与 `/v6/priv_url`、`/v5/url` 专属 `key` 签名算法，所有请求由手机端直连官方网关。

* **全流程账号与硬件级安全**：支持手机短信验证码登录（自动识别并支持同一手机号下多酷狗账号切换）与二维码扫码登录，登录凭证通过 **Android Keystore (AES-GCM)** 硬件级加密持久化。

* **每日畅听 VIP 自动续期**：支持概念版每日签到领取畅听 VIP、升级 VIP 权益、CSCC 三段式真实听歌时长上报，以及基于系统 `JobScheduler` 的后台静默签到守护（`VipGuardianJobService`）。

* **完整曲库生态**：覆盖每日推荐、私人 FM、新歌速递（按发行日期与拼音首字母双维排序）、官方排行榜、歌单广场、歌手/专辑详情及四级楼中楼评论区（支持酷狗原生动态表情与评论图片预览）。

### 2. 高解析音源、硬件级 `STREAMINFO` 探测与 64-bit 母带 DSP

* **多音质智能解析与回退**：通过 `/v2/get_res_privilege/lite` 补全 `res_hash`（Hi-Res）、`sq_hash`（SQ 无损 FLAC）、`320_hash`（HQ 高品）与 `ogg_128_hash`（标准），按用户偏好音质自动平滑降级，支持移动网络省流保护。

* **FLAC `STREAMINFO` 硬件级真实采样率探测**：针对酷狗 API 不返回采样率字段的问题，遵循 [RFC 9639](https://www.rfc-editor.org/info/rfc9639) 标准通过轻量 HTTP `Range: bytes=0-511` 请求直接解析音频流前 42\~512 字节的 **FLAC `STREAMINFO` 块**（以及 OGG `vorbis` 识别头、MP3 MPEG 帧头），精准还原 `44.1 kHz / 48.0 kHz / 96.0 kHz / 192.0 kHz` 真实采样率与 `16bit / 24bit` 位深（与椒盐音乐 Salt Player 硬件信息完全一致）。

* **64-bit/Float 母带级 DSP 引擎**：内置 `MasterAudioProcessor` 与 `ViperDspEngine`，提供 10 段参量均衡器（PEQ）、蝰蛇音效预设、动态超重低音、人声清澈度、电子管温暖度激励、M/S 立体声场混响与等响度补偿。

* **进阶播放控制**：支持高潮副歌速刷（Climax 30 秒精华连播）、KTV 纯伴奏实时切换、跨曲无缝淡入淡出（Crossfade 0\~12 秒）、ReplayGain 音量平衡，以及 0.5x\~2.0x 倍速与升降调独立/联动控制。

### 3. KRC 逐字流光歌词、全局穿透桌面歌词与 2K 海报

* **端侧 KRC 逐字解密引擎**：内置 16 字节密钥循环 XOR 异或 + Zlib Inflate 解压器，毫秒级解析行级与字级时间戳、双语翻译及日/韩罗马音注音（纯中文歌曲自动隐藏译文/音译按钮）。

* **沉浸式舞台歌词渲染**：支持字内卡拉 OK 平滑扫光、前奏/间奏呼吸等待波点、阶梯景深高斯模糊、双行预览及点击任意歌词行直接跳转播放（Seek）。

* **系统级跨应用桌面歌词**：支持横向全屏自适应宽度、双行插空居中与长句自然三行换行（容器高度恒定不抖动）、封面动态取色实时随动、物理点击穿透锁定，以及系统通知栏/流体云快捷开关与解锁按钮。

* **2K 超清歌词海报生成**：支持多选歌词段落、切换多款视觉排版模板，一键渲染 2K 分辨率 PNG 海报并保存至系统相册。

### 4. 多音质本地下载与零依赖高清封面/歌词二进制内嵌

* **纯 Kotlin 二进制元数据内嵌器 (`AudioMetadataEmbedder`)**：参考 [lisalee23042605/kgmusicbot](https://github.com/lisalee23042605/kgmusicbot) 并在端侧实现零外部依赖的二进制标签写入：

  * **FLAC (`fLaC`)**：解析并重组 Metadata Block 链表，写入 `Vorbis Comment (Block Type 4)`（包含 `TITLE`、`ARTIST`、`ALBUM`、`ALBUMARTIST`、`DATE`、`COMMENT`、`LYRICS`、`UNSYNCEDLYRICS`）与 `Picture (Block Type 6, type=3 Front Cover)`。

  * **MP3 (`ID3v2.3`)**：构建 Synchsafe 头部与 UTF-16LE (with BOM) 编码的 `TIT2`、`TPE1`、`TPE2`、`TALB`、`TYER`、`TDRC`、`COMM`、`USLT`（内嵌时间轴歌词）及 `APIC`（内嵌高清封面）帧。

  * **OGG (`OggS`)**：自动通过文件前 4 字节魔数识别容器类型，保护 OGG 音频页同步头不被破坏。

* **默认内嵌 1000P 高清封面与歌词 + 可选独立 `.lrc` 文件**：下载时自动将酷狗封面 URL 升级为 `1000×1000` 高清大图内嵌，并支持自定义是否同步在歌词目录生成同名 `.lrc` 文件。

* **自定义保存路径与独立「下载」设置中心**：支持通过系统 SAF 文件夹选择器（`OpenDocumentTree`）或手动输入自定义音乐下载路径与歌词保存路径，并提供独立的「最近下载记录」二级管理页面。

### 5. Material 3 流体视觉、外设线控与透明权限管控

* **封面色相锁定 13 级动态取色**：提取当前播放歌曲专辑封面主色，构建色相锁定（Hue-Locked）的 13 级明度色阶（严格满足 WCAG 4.5:1 对比度），驱动仿真黑胶唱盘、圆角方图封面与五款流光背景。

* **120Hz/144Hz 高刷与横竖屏自适应舞台**：启动时主动申请屏幕最高物理刷新率，结合倾角传感器支持横放手机平滑切换至横屏黑胶/歌词舞台。

* **多应用混音同播与耳机线控**：支持三档音频焦点模式（独占焦点 / 压低音量闪避同播 Ducking / 完全混音同播），提供有线耳机中键双击/三击切歌方向定制、灵敏度调节（280ms/350ms/450ms）、连接耳机自动续播及蓝牙歌词延迟自动补偿。

* **最小化透明权限管控**：在「设置 -> 权限管理」中完整公开全部 8 项权限的获取用途、系统权限标识、隐私承诺与关闭影响，支持随时独立开关。

## 三、技术栈概览

| 领域 | 采用技术与方案 | 
| ----- | ----- | 
| **开发语言与并发** | Kotlin 1.9+ · Kotlin Coroutines & StateFlow / SharedFlow | 
| **声明式 UI 与动效** | Jetpack Compose (BOM 2024.06) · Material 3 · Canvas 自定义绘制 · 120Hz 高刷适配 | 
| **音频播放与会话** | AndroidX Media3 (`ExoPlayer` · `MediaSessionService` · `SimpleCache` 边听边存) | 
| **母带级音频 DSP** | 自研 64-bit Float PCM `MasterAudioProcessor` · `ViperDspEngine` (Biquad PEQ / 混响 / 动态低音) | 
| **网络与协议签名** | OkHttp 4.12 · Retrofit 2.11 · Gson · 纯端侧 `KugouSigner` (MD5 / AES / RSA / Zlib) | 
| **音频标签二进制引擎** | 自研零依赖 `AudioMetadataEmbedder` (FLAC `STREAMINFO`/Vorbis/Picture & MP3 `ID3v2.3` APIC/USLT) | 
| **图片加载与色彩提取** | Coil Compose 2.6 (含 GIF/WebP 动图支持) · 自研 `CoverTonalPalette` 13 级色阶算法 | 
| **安全与本地持久化** | Android Keystore (AES-GCM 硬件级加密) · SharedPreferences · SWR 歌单秒开快照缓存 | 

## 四、项目目录结构

```
BreezeMusic/
├── app/
│   ├── build.gradle.kts                         # 模块级 Gradle 构建配置、依赖声明与编译选项
│   ├── proguard-rules.pro                       # Release 混淆与 R8 压缩保留规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml              # 应用组件、前台媒体服务、桌面小组件与权限声明
│       │   ├── java/com/concept/music/
│       │   │   ├── ConceptMusicApp.kt           # Application 入口：全局单例初始化与后台守护调度
│       │   │   ├── MainActivity.kt              # 宿主 Activity：高刷申请、开屏动效宿主与全局导航协调
│       │   │   ├── data/
│       │   │   │   ├── kugou/                   # 酷狗概念版 (appid=3116) 纯端侧协议层
│       │   │   │   │   ├── KugouSigner.kt       # 核心签名算法、RSA/AES 加解密、KRC 解密与设备 ID 生成
│       │   │   │   │   ├── KugouTransport.kt    # OkHttp 统一网关传输层、自动重试与多域名路由
│       │   │   │   │   ├── AuthRepository.kt    # 短信验证码登录、扫码登录、dfid 注册与 Token 续期
│       │   │   │   │   ├── AccountRepository.kt # Android Keystore 加密存储与多账号无缝切换管理
│       │   │   │   │   ├── MusicRepository.kt   # 音源解析 (/v6/priv_url & /v5/url)、曲库搜索、推荐与歌单接口
│       │   │   │   │   ├── LyricRepository.kt   # KRC 逐字歌词/双语译文/罗马音解析与标准 .lrc 导出格式化
│       │   │   │   │   └── VipClaimRepository.kt# 概念版每日签到领 VIP、升级 VIP 与 CSCC 听歌时长上报
│       │   │   │   ├── model/                   # 领域数据模型层
│       │   │   │   │   ├── Song.kt              # 歌曲、歌单、音质档位 (AudioQuality) 与多 Hash 模型
│       │   │   │   │   └── Comment.kt           # 评论楼中楼、酷狗表情解析与歌曲技术参数 (SongDetailInfo)
│       │   │   │   └── vip/
│       │   │   │       └── VipManager.kt        # 全局曲库、歌词、账号与 VIP 仓库门面 (Facade)
│       │   │   ├── service/                     # 后台服务、DSP 音频引擎与系统级能力管理
│       │   │   │   ├── PlaybackService.kt       # Media3 前台播放服务、音频焦点、耳机线控与流体云通知
│       │   │   │   ├── PlaybackCoordinator.kt   # 播放队列调度、无缝预热、高潮试听与跨曲淡入淡出控制
│       │   │   │   ├── AudioEffectManager.kt    # 64-bit 母带 DSP 处理器、10 段参量均衡器与蝰蛇音效引擎
│       │   │   │   ├── AudioCacheManager.kt     # Media3 SimpleCache 流媒体缓存与四维分项缓存统计/清理
│       │   │   │   ├── AudioMetadataEmbedder.kt # 零依赖 FLAC/MP3 标签、1000P 高清封面与歌词内嵌 + STREAMINFO 解析
│       │   │   │   ├── MusicDownloadManager.kt  # 多音质下载队列、自定义音乐/歌词路径与独立 .lrc 导出管理
│       │   │   │   ├── SystemLyricBridge.kt     # 跨应用全局悬浮桌面歌词窗口管理、双向同步与穿透锁定
│       │   │   │   ├── AppPermissionManager.kt  # 8 大权限公开透明说明、系统授权跳转与独立开关管控
│       │   │   │   ├── VipGuardianManager.kt    # JobScheduler 每日静默自动领取概念版 VIP 后台任务
│       │   │   │   └── ConceptMusicWidgetUpdater.kt # 桌面音乐小组件状态同步与远程视图渲染
│       │   │   └── ui/                          # Jetpack Compose 声明式界面层
│       │   │       ├── account/                 # 登录页 (验证码/扫码)、账号切换与安全验证弹窗
│       │   │       ├── comment/                 # 四级楼中楼评论区、热评/最新切换与大图查看器
│       │   │       ├── components/              # 核心复用组件库
│       │   │       │   ├── BreezeBrandSplash.kt # 统一微风音乐品牌图标 (BreezeAppLogo)、版本读取与开屏动效页
│       │   │       │   ├── NowPlayingSheet.kt   # 全屏黑胶/方图/逐字歌词沉浸式播放舞台（含横竖屏自适应）
│       │   │       │   ├── NowPlayingDialogs.kt # 播放页更多控制台、音质选择、倍速变调、睡眠定时与 2K 海报
│       │   │       │   ├── SongDownloadDialog.kt# 单曲多音质下载选择与内嵌开关弹窗
│       │   │       │   ├── SongDetailSheet.kt   # 音乐技术参数与特征码详情面板（实时展示硬件采样率/位深）
│       │   │       │   ├── ViperSoundEffectScreen.kt # 蝰蛇音效与 10 段参量均衡器可视化调音台
│       │   │       │   └── FloatingLyricOverlay.kt   # 全局悬浮桌面歌词与内嵌歌词胶囊渲染组件
│       │   │       ├── home/                    # 发现页、每日推荐、私人 FM、新歌速递与排行榜详情
│       │   │       ├── library/                 # 我的音乐库、自建/收藏歌单、歌手主页与专辑详情页
│       │   │       ├── search/                  # 搜索页、热搜榜、历史记录与多维综合搜索结果页
│       │   │       ├── settings/                # 二级分类设置中心 (SettingsScreen.kt) 与可折叠更新日志
│       │   │       ├── theme/                   # 深海蓝/水彩浅色主题、封面 13 级动态取色与流体物理动效曲线
│       │   │       └── viewmodel/               # 播放器、发现页、音乐库与搜索 ViewModel 状态管理
│       │   └── res/                             # Android 资源目录
│       │       ├── drawable/                    # 自适应图标矢量前景/背景、开屏窗口背景与通知栏图标
│       │       ├── mipmap-anydpi-v26/           # Android 8.0+ Adaptive Icon 定义 (含 Monochrome 主题图标)
│       │       ├── mipmap-mdpi ~ xxxhdpi/       # 全分辨率圆角与圆形桌面图标 PNG
│       │       ├── layout/                      # 桌面小组件 RemoteViews 布局
│       │       ├── values/                      # 字符串、主题色与无闪烁冷启动窗口样式 (themes.xml)
│       │       └── xml/                         # 网络安全配置与小组件元数据
│       └── test/java/com/concept/music/         # 单元测试套件
│           ├── UiAlignmentRegressionTest.kt     # 协议解析、色彩对比度、桌面歌词、元数据内嵌与 STREAMINFO 回归测试
│           └── AudioEnhancementTest.kt          # 64-bit DSP 音频管线、均衡器滤波与音效处理单元测试
├── build.gradle.kts                             # 根项目构建脚本
├── settings.gradle.kts                          # Gradle 仓库与模块配置
└── README.md                                    # 项目说明文档

```

## 五、快速开始与本地构建打包

### 1. 环境要求

* **JDK 版本**：JDK 17（Android Studio Ladybug / Koala / Iguana 内置 JBR 17 即可）

* **Android SDK**：`minSdk = 26` (Android 8.0)，`compileSdk = 34` (Android 14)

* **Gradle**：项目自带 Gradle Wrapper (`./gradlew` / `gradlew.bat`)，无需手动安装

### 2. 编译 Debug 安装包（本地调试推荐）

在项目根目录打开终端执行：

```
# macOS / Linux
./gradlew :app:assembleDebug

# Windows PowerShell / CMD
.\gradlew.bat :app:assembleDebug

```

编译完成后，生成的安装包路径为：
`app/build/outputs/apk/debug/app-debug.apk`

### 3. 运行单元测试套件

```
# macOS / Linux
./gradlew :app:testDebugUnitTest

# Windows PowerShell / CMD
.\gradlew.bat :app:testDebugUnitTest

```

### 4. 编译 Release 正式版安装包

如需编译启用 R8 代码优化与资源压缩的正式版 APK，可在 Android Studio 中选择 **Build -> Generate Signed App Bundle / APK...**，或在命令行执行：

```
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
