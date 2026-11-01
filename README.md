# BreezeMusic · 微风音乐

<p align="center">
<strong>耳畔微风起 · 帧享真无损</strong><br>
一款轻量、纯粹、高解析的第三方「酷狗概念版」Android 原生音乐客户端（支持手机版与平板版独立双端）
</p>

<p align="center">
<img src="https://img.shields.io/badge/Version-v1.3.2-00E5FF?style=flat-square" alt="Version" />
<img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3898FF?style=flat-square" alt="Platform" />
<img src="https://img.shields.io/badge/Language-Kotlin%201.9-7F52FF?style=flat-square" alt="Kotlin" />
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20120fps%20Spring%20Motion-00E5FF?style=flat-square" alt="Compose" />
<img src="https://img.shields.io/badge/Audio-Media3%20ExoPlayer%20%2B%2064bit%20Pure%20LTI%20DSP-00E5A3?style=flat-square" alt="Audio" />
<img src="https://img.shields.io/badge/Architecture-Pure%20Client--Side%20Direct-FFD54F?style=flat-square" alt="Direct" />
<img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License" />
</p>

## 一、项目简介

**BreezeMusic（微风音乐）** 是基于 **Kotlin + Jetpack Compose (Material 3) + AndroidX Media3** 构建的酷狗概念版第三方原生 Android 客户端，提供 **手机端 (`phone`)** 与 **平板端 (`tablet`)** 独立双签名变体。

项目将 [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) 中酷狗概念版（`appid = 3116`、`clientver = 11440`）的全套签名算法、RSA/AES 加解密、风控设备注册（`dfid`）与音源解析协议 **100% 纯 Kotlin 端侧直连化**，不依赖任何 Node.js、Python 或 Go 第三方中转服务器。在此基础上，深度打造了 **单层 96dp 极速双色启动页、Hi-Res 真实流头解析、64-bit 纯线性双二阶 (Pure LTI Biquad) 母带级 DSP 调音台与全功能参量均衡器、五款高定动态播放背景（含无封面三色流体极光与实时音频同频律动光晕）、KRC 逐字流光与系统级穿透桌面歌词、全局 120fps 欠阻尼弹性物理动效与方向性运动虚化、全链路低功耗/熄屏零唤醒守护、多音质下载与 1000P 高清封面/双语歌词二进制内嵌** 等核心体验。

## 二、核心特性与功能介绍

### 1. 纯端侧直连与概念版 VIP 权益闭环

* **零后端端侧直连**：纯 Kotlin 实现酷狗概念版 `signature`、`signatureTracker`、`register.dev` 设备风控注册与 `/v6/priv_url`、`/v5/url` 专属 `key` 签名算法，所有请求由手机/平板端直连官方网关，内置国内免 VPN 多通道容灾路由。
* **全流程账号与硬件级安全**：支持手机短信验证码登录（自动识别并支持同一手机号下多酷狗账号切换）与二维码扫码登录，登录凭证通过 **Android Keystore (AES-GCM)** 硬件级加密持久化。
* **每日畅听 VIP 自动续期**：支持概念版每日签到领取畅听 VIP、升级 VIP 权益、CSCC 三段式真实听歌时长上报，精简「我的」页 VIP 权益卡片文案（每日签到 · 畅享无损），并集成基于系统 `JobScheduler` 的后台静默签到守护（`VipGuardianJobService`）。
* **完整曲库生态与双向收藏同步**：覆盖每日推荐（含当前播放歌曲一键平滑居中定位按钮）、私人 FM、新歌速递（按发行日期与拼音首字母双维排序）、官方排行榜、歌单广场、歌手/专辑详情及多级楼中楼评论区；深度对齐 `KuGouMusicApi` 的 `/v2/collect_song_list` 与 `/v2/delete_song_list` 接口，实现搜索歌单、推荐歌单与「我的」页收藏歌单的双向实时状态同步（「收藏 / 已收藏」即时切换与再次点击取消收藏）。

### 2. 高解析音源、硬件级 `STREAMINFO` 探测与 64-bit 纯线性母带 DSP

* **多音质智能解析与回退**：通过 `/v2/get_res_privilege/lite` 补全 `res_hash`（Hi-Res）、`sq_hash`（SQ 无损 FLAC）、`320_hash`（HQ 高品）与 `ogg_128_hash`（标准），按用户偏好音质自动平滑降级，支持移动网络省流保护与未登录访客态音源解析。
* **FLAC `STREAMINFO` 硬件级真实采样率探测**：遵循 [RFC 9639](https://www.rfc-editor.org/info/rfc9639) 标准通过轻量 HTTP `Range: bytes=0-511` 请求直接解析音频流前 42~512 字节的 **FLAC `STREAMINFO` 块**（以及 OGG Vorbis 识别头、MP3 MPEG 帧头），精准还原 `44.1 kHz / 48.0 kHz / 96.0 kHz / 192.0 kHz` 真实采样率与 `16bit / 24bit` 位深。
* **64-bit 纯线性时不变 (Pure LTI Biquad) 母带级 DSP 引擎**：
  * **零谐波与零互调失真架构 (`ConceptMasteringAudioProcessor`)**：全链路采用 64-bit 双精度 Direct Form I 双二阶线性时不变（LTI）滤波器网络（`THD = 0.000%`、`IMD = 0.000%`），彻底根除时域非线性谐波畸变器与伪随机抖动造成的电流滋滋声；内置预设 EQ 与声场附加滤波的智能防重叠增益协同（Overlap Scaling）与温和峰值余量校准，彻底消除开启音效后的音量骤降或低频浑浊过载。
  * **声场调音工作台 (`AcousticFieldTuningTab`)**：提供超重低音下潜、低频瞬态弹性冲击、6 档等宽低频分频谐振中心频点（`40Hz 极深潜` ~ `110Hz 暖厚`）、瞬态微动态与鼓点清晰度、3D 环绕声场宽度、舞台纵深与前后距离感、Bauer 耳机交叉馈送（消除头中效应）、360° 动态环绕轨迹速率、带通净化与独立预延迟（Pre-Delay 0~80ms）/高频阻尼的 Schroeder 空间声学混响矩阵、人声通透与 6.2kHz 齿音柔化抑制（De-Esser）、12kHz+ 极高频空气泛音及黑胶胆机温暖度。
  * **发烧级 10 段全参数均衡器 (`ProfessionalEqualizerPage`)**：支持 10 段独立增益（`-10dB ~ +10dB`，配 `-0.1 / +0.1 dB` 步进微调器）、连续中心频点微调、品质因数 `Q` 值（`0.30 ~ 8.00` 及 4 档快捷按钮）、5 种滤波器拓扑（`Peak / Low-Shelf / High-Shelf / Notch / Band-Pass`）、高通/低通边界滤波（`HPF / LPF`）、一键 A/B 原音直通对比、5 大发烧目标参考曲线（哈曼曲线 / 弥散场参考 / 人声丝绒 / HiFi深潜低频 / 监听平直），以及支持**存入、点击载入、单槽独立删除/重置与一键重置全部槽位**的 3 组自定义记忆槽（`A / B / C`）。
* **进阶播放控制**：支持高潮副歌速刷（Climax 30 秒精华连播）、KTV 纯伴奏实时切换、跨曲无缝淡入淡出（Crossfade）、ReplayGain 音量平衡，以及 0.5x~2.0x 倍速与升降调独立/联动控制。

### 3. 五款高定动态播放背景与连续动量过渡

* **封面虚化 (`COVER_BLUR`)**：保留原版柔和封面视觉与双径向色光晕染，统一 `400×400` 封面缓存规格，彻底解决切歌时因小尺寸缓存未命中导致的异常模糊跳变。
* **流体极光 (`FLUID_AURORA`)**：不显示任何专辑封面残影，通过 24-bin 色相直方图提取专辑封面的主色、副色、第三色共 **3 种流体色彩**，在深空底幕上交融演化出多频有机流体极光。
* **律动光晕 (`RHYTHM_HALO`)**：通过 `AudioRhythmTelemetry` 无锁读取音频渲染线程实时计算的三频能量（低频、中频、高频）与鼓点节拍脉冲，驱动三层同心圆光晕与当前播放歌曲同频呼吸律动。
* **暗夜星尘 (`STARLIGHT_NEBULA`)**：三团深空呼吸星云搭配三层视差漂移的柔和圆点星尘（无生硬十字线条）。
* **深邃纯黑 (`DEEP_OLED`)**：针对 OLED 屏幕定制的极简省电微光背景。
* **播放 ↔ 暂停连续动量过渡 (`playMomentum`)**：流体极光、律动光晕、暗夜星尘三大动态背景采用基于帧时钟与 `playMomentum`（`880ms FastOutSlowInEasing`）的连续相位积分器，从播放切到暂停时背景流体、光晕与星尘在当前位置平滑减速停驻，从暂停切回播放时从当前位置柔和加速恢复，彻底消除暂停/播放切换时的位置跳变。

### 4. KRC 逐字流光歌词、全局穿透桌面歌词与 2K 海报

* **端侧 KRC 逐字解密引擎**：内置 16 字节密钥循环 XOR 异或 + Zlib Inflate 解压器，毫秒级解析行级与字级时间戳、双语翻译及日/韩罗马音注音（纯中文歌曲自动隐藏译文/音译按钮）。
* **沉浸式舞台歌词与双行三列配色网格**：支持字内卡拉 OK 平滑扫光、基于 $O(1)$ 预判（`hasInterludeCandidateAt`）的前奏/间奏三点流光呼吸波点、连续光学景深高斯模糊、双行预览及点击任意歌词行直接跳转播放（Seek）；歌词显示设置采用 **2×3 双行三列自适应网格** 完整展示「冰晶青蓝 / 极光幻紫 / 鎏金琥珀 / 霓虹珊瑚 / 翡翠薄荷 / 封面随动」六大四字主题名。
* **系统级跨应用桌面歌词**：支持横向全屏自适应宽度、双行插空居中与长句自然三行换行、封面动态取色实时随动、物理点击穿透锁定、锁屏熄屏 `ACTION_SCREEN_OFF` 自动挂起 0fps 休眠，以及系统通知栏/OPPO 流体云快捷开关与解锁按钮。
* **2K 超清歌词海报生成**：支持多选歌词段落、切换多款视觉排版模板，一键渲染 2K 分辨率 PNG 海报并保存至系统相册。

### 5. 多音质本地下载与零依赖高清封面/歌词二进制内嵌

* **纯 Kotlin 二进制元数据内嵌器 (`AudioMetadataEmbedder`)**：参考 [lisalee23042605/kgmusicbot](https://github.com/lisalee23042605/kgmusicbot) 并在端侧实现零外部依赖的二进制标签写入（支持 FLAC `fLaC` Vorbis Comment/Picture、MP3 `ID3v2.3` UTF-16LE/APIC/USLT 与 OGG 容器保护）。
* **默认内嵌 1000P 高清封面与歌词 + 可选独立 `.lrc` 文件**：下载时自动将酷狗封面 URL 升级为 `1000×1000` 高清大图内嵌，并支持自定义是否同步在歌词目录生成同名 `.lrc` 文件。

### 6. 全局 120fps 弹性物理动效与抽屉联动架构

* **`ConceptMotion` 五维弹簧体系**：提供 `coplanarTabSpring` / `spatialPageSpring` / `spatialSheetSpring` / `categoryContentSpring` / `floatingPopupSpring` 物理弹簧模型。
* **抽屉弹窗与悬浮播放条/底栏丝滑联动**：`ConceptAnimatedOverlaySheet` 支持退场瞬间触发 `onDismissStart` 同步回调，配合 `MainActivity` 统一的 `isMainSubSheetOpen` 状态汇总与无跳变渲染层级，使发现页（猜你喜欢、全部榜单、随心听电台）与我的页（心流舱紧凑自适应高度抽屉）在打开和关闭抽屉时，悬浮播放条与底部导航栏均能以一致的欠阻尼弹簧动画自然退场与丝滑浮现。

## 三、技术栈概览

| 领域 | 采用技术与方案 |
| :--- | :--- |
| **开发语言与并发** | Kotlin 1.9+ · Kotlin Coroutines & StateFlow / SharedFlow · 前后台自适应节流 |
| **声明式 UI 与物理动效** | Jetpack Compose (BOM 2024.06) · Material 3 · `ConceptMotion` 欠阻尼弹簧体系 · `RenderEffect` 方向性运动虚化 · 120Hz 零重组绘制管线 |
| **音频播放与会话** | AndroidX Media3 (`ExoPlayer` · `MediaSessionService` · `SimpleCache` 边听边存 · 120ms~360ms 低延迟 PCM 缓冲) |
| **母带级音频 DSP** | 自研 64-bit 纯线性 LTI `ConceptMasteringAudioProcessor` (10段全参数 Biquad PEQ / 防重叠声场滤波 / Schroeder 混响 / 0dB 平直频段短路) |
| **网络与协议签名** | OkHttp 4.12 · Retrofit 2.11 · Gson · 纯端侧 `KugouSigner` (MD5 / AES / RSA PKCS1 / Zlib · 免 VPN 多路容灾) |
| **音频标签二进制引擎** | 自研零依赖 `AudioMetadataEmbedder` (FLAC `STREAMINFO`/Vorbis/Picture & MP3 `ID3v2.3` APIC/USLT) |
| **图片加载与色彩提取** | Coil Compose 2.6 (`normalizeKugouThumbUrl` 缩略图降维 + 400P 封面缓存对齐) · 24-bin 三色特征提取与 `CoverTonalPalette` 13 级色阶算法 |
| **安全与本地持久化** | Android Keystore (AES-GCM 硬件级加密) · SharedPreferences · SWR 歌单秒开快照缓存 |

## 四、项目目录结构（模块化高内聚架构）

项目按照职责边界进行了严谨的模块化拆分，消除巨型单文件耦合，确保高内聚、低耦合与良好的长期可维护性：

```
BreezeMusic/
├── app/
│   ├── build.gradle.kts                         # 模块级 Gradle 构建配置 (versionName = "1.3.2", phone/tablet 双变体独立签名)
│   ├── proguard-rules.pro                       # Release 混淆与 R8 压缩保留规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml              # 应用组件、前台媒体服务、桌面小组件与权限声明
│       │   ├── java/com/concept/music/
│       │   │   ├── ConceptMusicApp.kt           # Application 入口：全局单例按需初始化与 Coil 图像管线配置
│       │   │   ├── MainActivity.kt              # 宿主 Activity：双套启动页持久化、120Hz 高刷申请与同平面导航/抽屉联动宿主
│       │   │   ├── data/
│       │   │   │   ├── kugou/                   # 酷狗概念版 (appid=3116) 纯端侧协议层
│       │   │   │   │   ├── KugouSigner.kt       # 核心签名算法、RSA(含PKCS1)/AES 加解密、KRC 解密与设备 ID 生成
│       │   │   │   │   ├── KugouTransport.kt    # OkHttp 统一网关传输层、免 VPN 容灾路由与自动重试
│       │   │   │   │   ├── KrcDecoder.kt        # KRC 逐字歌词 XOR+Zlib 解密、HTML 实体反转义与双语/音译提取
│       │   │   │   │   ├── KugouCsccReporter.kt # 概念版 CSCC 听歌时长心跳上报器
│       │   │   │   │   ├── AuthRepository.kt    # 短信验证码登录、扫码登录、dfid 注册与 Token 续期
│       │   │   │   │   ├── AccountRepository.kt # Android Keystore 加密存储与多账号无缝切换管理
│       │   │   │   │   ├── MusicRepository.kt   # 音源解析、曲库搜索、推荐、双向歌单收藏/取消收藏与蝰蛇音效接口
│       │   │   │   │   ├── LyricRepository.kt   # 歌词检索与标准 .lrc 导出格式化
│       │   │   │   │   ├── VipRepository.kt     # 概念版每日签到领 VIP 与升级 VIP 接口
│       │   │   │   │   ├── SessionStore.kt      # 会话凭证安全持久化存储接口
│       │   │   │   │   ├── VerificationCoordinator.kt # 验证码安全校验协调器
│       │   │   │   │   └── Models.kt            # 协议层基础传输模型
│       │   │   │   ├── model/                   # 领域数据模型层 (Song / Lyric / Playlist / Comment / VipStatus / Discover / Library / Search)
│       │   │   │   ├── repository/              # 本地播放历史与搜索历史持久化仓库
│       │   │   │   └── vip/
│       │   │   │       ├── VipManager.kt        # 全局曲库、歌词、账号与 VIP 仓库门面 (Facade)
│       │   │   │       └── ListeningGradeManager.kt # 听歌等级与时长统计管理
│       │   │   ├── service/                     # 后台服务、64-bit DSP 音频引擎与系统级能力管理
│       │   │   │   ├── PlaybackService.kt       # Media3 前台播放服务、耳机多击线控、三档音频焦点与熄屏零唤醒
│       │   │   │   ├── ConceptMasteringAudioProcessor.kt # 独立 64-bit 纯线性 LTI Biquad 母带级音频处理器与实时律动遥测采样
│       │   │   │   ├── AudioEffectManager.kt    # 10 段全参数均衡器 (含记忆槽存取/删除/重置)、声场调音与蝰蛇音效状态中心
│       │   │   │   ├── PlaybackCoordinator.kt   # 播放队列调度、无缝预热、高潮试听与跨曲淡入淡出控制
│       │   │   │   ├── AudioCacheManager.kt     # Media3 SimpleCache 流媒体缓存与四维分项缓存统计/清理
│       │   │   │   ├── AudioMetadataEmbedder.kt # 零依赖 FLAC/MP3 标签、1000P 高清封面与歌词内嵌 + STREAMINFO 解析
│       │   │   │   ├── MusicDownloadManager.kt  # 多音质下载队列、自定义音乐/歌词路径与独立 .lrc 导出管理
│       │   │   │   ├── SystemLyricBridge.kt     # 跨应用全局悬浮桌面歌词窗口管理、零分配渲染与穿透锁定
│       │   │   │   ├── FocusPodManager.kt       # 心流专注舱番茄钟倒计时与白噪音联动状态中心
│       │   │   │   ├── AmbientSoundManager.kt   # 实时程序化环境白噪音合成引擎
│       │   │   │   ├── AppPermissionManager.kt  # 8 大权限公开透明说明、系统授权跳转与独立开关管控
│       │   │   │   ├── VipGuardianManager.kt    # JobScheduler 每日静默自动领取概念版 VIP 后台任务
│       │   │   │   └── ConceptMusicWidgetUpdater.kt # 桌面音乐小组件状态同步与远程视图渲染
│       │   │   └── ui/                          # Jetpack Compose 声明式界面层（按职责模块化拆分）
│       │   │       ├── account/                 # 登录页 (验证码/扫码)、账号切换与安全验证弹窗
│       │   │       ├── comment/                 # 多级楼中楼评论区（含滑动防抖加载）、热评/最新切换与大图查看器
│       │   │       ├── components/              # 核心播放器与通用组件库
│       │   │       │   ├── NowPlayingSheet.kt               # 播放页主容器、5 款动态背景（含三色无封面流体极光与平滑暂停动量）与封面页布局
│       │   │       │   ├── NowPlayingTurntableAndCover.kt   # 拟真黑胶唱盘、圆角流光封面与三色动态色板提取器
│       │   │       │   ├── NowPlayingCanvasControls.kt      # 光学彗星拖尾进度条与自绘圆润矢量播放控制图标
│       │   │       │   ├── NowPlayingDedicatedLyricPage.kt  # 独立高定逐字歌词舞台、间奏呼吸灯与无级横滑跳转手势
│       │   │       │   ├── NowPlayingDialogs.kt             # 歌词显示设置（2x3 主题网格）、更多控制台、音质/倍速/睡眠定时与多歌手选择弹窗
│       │   │       │   ├── PlayQueueBottomSheet.kt          # 一体化玻璃拟态播放队列与最近队列层叠卡片抽屉
│       │   │       │   ├── AudioEffectDialog.kt             # 蝰蛇音效库、声场调音台与发烧级 10 段参量均衡器 (PEQ)
│       │   │       │   ├── FocusPodBottomSheet.kt           # 自适应紧凑高度的心流专注舱与白噪音抽屉
│       │   │       │   ├── LandscapePlayerPage.kt           # 平板与横屏沉浸式双栏黑胶/歌词合并舞台
│       │   │       │   ├── LyricPosterDialog.kt             # 2K 超清歌词海报生成与模板定制弹窗
│       │   │       │   └── ...                              # MiniPlayerBar / SongItemRow / PlaylistCard / VipCenterBottomSheet 等复用组件
│       │   │       ├── home/                    # 发现页与推荐模块
│       │   │       │   ├── HomeScreen.kt                    # 发现页主界面、私人 FM 卡片、场景电台画廊与每日推荐页（含当前播放定位）
│       │   │       │   ├── RankAndNewSongScreens.kt         # 新歌速递时间轴全屏页、巅峰排行榜详情页与全部榜单抽屉
│       │   │       │   └── RadioScreen.kt                   # 随心听电台频道抽屉与分类电台列表
│       │   │       ├── library/                 # 我的音乐库（绘制期联动顶栏）、歌单详情（双向收藏同步）、歌手主页与专辑详情页
│       │   │       ├── navigation/              # 手机底部悬浮胶囊导航栏与平板左侧垂直胶囊导航栏
│       │   │       ├── search/                  # 搜索页、热搜榜、历史记录与多维综合搜索结果页
│       │   │       ├── settings/                # 模块化二级设置中心
│       │   │       │   ├── SettingsScreen.kt                # 设置主框架、外观/音质/外设控制、无边框组件与多源版本更新检查
│       │   │       │   ├── SettingsDownloadAndPermissions.kt # 独立下载设置、下载记录子页、四维存储缓存明细与 8 大权限透明管控中心
│       │   │       │   └── SettingsChangelogAndAbout.kt     # 全版本可折叠更新日志、GitHub Issue 可视化反馈指南与关于页面
│       │   │       ├── theme/                   # 深浅双主题、WindowSizeClass 平板自适应断点、封面 13 级动态取色与 ConceptMotion 弹性动效引擎
│       │   │       └── viewmodel/               # 播放器（含前后台自适应节流）、发现页、音乐库与搜索 ViewModel
│       │   └── res/                             # Android 资源目录（含 96dp 深浅双套启动页图标与样式）
│       └── test/java/com/concept/music/         # 单元测试套件 (UiAlignmentRegressionTest / AudioEnhancementTest / SongPlaybackSwitchTest 等)
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

项目内置 `phone`（手机端，`phoneSign` 签名）与 `tablet`（平板端，`tabletSign` 签名，包名后缀 `.tablet`）双构建变体：

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
