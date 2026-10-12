# ConceptMusic（酷狗概念版第三方）项目诊断、重构规范与创新蓝图

> **文档定位**：本文件为项目首席架构与全栈实施规范清单（临时查阅与迭代执行追踪文档）。  
> **技术底座**：Kotlin + Jetpack Compose + Material Design 3 + AndroidX Media3 (ExoPlayer) + 零后端原生酷狗概念版协议引擎（`appid=3116`, `clientver=11440`）。  
> **参考基准**：
> - [MakcRe/KuGouMusicApi](file:///e:/vibe%20coding/kugougainianban/repo_kugou_api/README.md)（协议签名、KRC 解密、概念版 VIP 接口规范）
> - [umr-xiaomai/kgka_Music_hl](file:///e:/vibe%20coding/kugougainianban/repo_kgka/README.md)（概念版福利流、SWR 缓存、倍速、睡眠定时、听歌统计）
> - [zzyoxml/md3Music](file:///e:/vibe%20coding/kugougainianban/repo_md3/README.md)（MD3 Expressive 流体动效、KRC 逐字辉光/景深模糊歌词、多风格播放页、系统级歌词挂载）

---

## 一、 现存代码级 BUG 与架构断层清单（P0 - 优先止血）

### 1. 主题与色彩系统冲突（浅色模式下文字隐身）
- [x] **[BUG-01] 深浅色模式混用导致浅色模式下列表文字不可见**
  - **问题位置**：
    - [Theme.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/theme/Theme.kt#L42-L61)（默认 `darkTheme = isSystemInDarkTheme()`, `dynamicColor = true`）
    - [HomeScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/home/HomeScreen.kt#L70)、[LibraryScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/LibraryScreen.kt#L99)、[SearchScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/search/SearchScreen.kt#L87)（硬编码深色背景 `Color(0xFF0F1115)`）
    - [SongItemRow.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/SongItemRow.kt#L99)、[PlaylistDetailScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/PlaylistDetailScreen.kt#L103)、[AlbumDetailScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/AlbumDetailScreen.kt#L100)（使用 `MaterialTheme.colorScheme.onSurface` / `background`）
  - **缺陷表现**：系统处于浅色模式时，首页/搜索页背景仍为纯黑 `0xFF0F1115`，而 `SongItemRow` 文字变为深黑 `TextPrimaryLight`，导致歌名隐身；且从黑色一级页面进入亮白色二级页面时出现刺眼黑白闪屏。
  - **修复方案**：移除一级页面所有硬编码背景色与文字色，统一收敛至 `MaterialTheme.colorScheme` 语义化颜色；在设置中提供「深色模式 / 浅色模式 / 跟随系统」开关（默认推荐概念版深色主题）。

### 2. 控件渲染与系统兼容性 BUG
- [x] **[BUG-02] 5 段均衡器（EQ）垂直推杆未旋转导致滑块截断**
  - **问题位置**：[AudioEffectDialog.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/AudioEffectDialog.kt#L331-L345)
  - **缺陷表现**：在宽 `28.dp`、高 `120.dp` 的 `Box` 内放置了 `Modifier.requiredWidth(110.dp)` 的水平 `Slider`，漏掉了 `-90f` 旋转变换，导致滑块横向溢出截断，无法上下推拉。
  - **修复方案**：为 `Slider` 添加 `.graphicsLayer { rotationZ = -90f }` 或使用自定义 Canvas 垂直推杆控件，并标注当前增益分贝值（如 `+3.0 dB`）。
- [x] **[BUG-03] Android 11 及以下设备播放页背景模糊失效**
  - **问题位置**：[NowPlayingSheet.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/NowPlayingSheet.kt#L116)
  - **缺陷表现**：`Modifier.blur(60.dp)` 仅在 Android 12 (API 31+) 生效，项目 `minSdk = 26`，在 Android 8~11 设备上会直接显示未模糊的原始封面图。
  - **修复方案**：引入 `androidx.palette:palette-ktx` 提取封面色板生成动态流体渐变背景（Mesh Gradient），或者在 Coil 加载封面时使用跨版本 Blur Transformation。

### 3. 假状态与断头路功能（Stub / Dead Wiring）
- [x] **[BUG-04] 播放页“红心收藏”按钮仅改变本地临时状态，未同步云端**
  - **问题位置**：[NowPlayingSheet.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/NowPlayingSheet.kt#L99) 及 [L209-L221](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/NowPlayingSheet.kt#L209-L221)
  - **缺陷表现**：`var isFavorite by remember(song.id) { mutableStateOf(song.isFavorite) }` 只翻转 UI 布尔值，未调用 `LibraryViewModel` 或 `MusicApiRepository` 将歌曲增删至云端“我喜欢”歌单。
  - **修复方案**：在全局状态层维护 `favoriteSongHashes: StateFlow<Set<String>>`，点击红心时调用云端歌单增删接口并乐观更新 UI。
- [x] **[BUG-05] 首页/搜索/专辑/歌手页的单曲“更多 (`⋮`)”按钮点击无响应且无播放高亮**
  - **问题位置**：
    - [SongItemRow.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/SongItemRow.kt#L45)
    - [HomeScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/home/HomeScreen.kt#L193-L197)、[SearchScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/search/SearchScreen.kt#L251-L255)、[AlbumDetailScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/AlbumDetailScreen.kt#L279-L283)、[ArtistDetailScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/ArtistDetailScreen.kt#L291-L295)
  - **缺陷表现**：除歌单详情页外，其余页面的 `SongItemRow` 均未传入 `onMoreClick` 和 `isPlaying` 参数，导致右侧三点按钮点击无效，且当前播放歌曲在列表中无高亮区分。
  - **修复方案**：构建全局通用的 `SongActionBottomSheet`，并在所有列表传入 `isPlaying = (currentSong?.id == song.id)` 与 `onMoreClick`。
- [x] **[BUG-06] 歌词自动滚屏抢占用户手动滑动（Scroll Hijacking）**
  - **问题位置**：[NowPlayingSheet.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/NowPlayingSheet.kt#L756-L763)
  - **缺陷表现**：`LaunchedEffect(currentLyricIndex)` 每逢下一句歌词就强制 `animateScrollToItem`，用户正在上下翻看歌词时会被强行拉回当前行。
  - **修复方案**：监听 `listState.isScrollInProgress` 及拖拽结束后的冷却计时器（如 4 秒内不自动滚屏），并显示中央时间尺与「跳转播放」按钮。
- [x] **[BUG-07] 导航栈与全屏弹窗布尔状态冲突**
  - **问题位置**：[MainActivity.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/MainActivity.kt#L85-L87) 及 [L239-L253](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/MainActivity.kt#L239-L253)
  - **缺陷表现**：从 `NowPlayingSheet` 点击评论时，强制关闭播放页（`showNowPlaying = false`）再打开评论覆盖层（`showCommentsScreen = true`），返回时无法回到播放页。
  - **修复方案**：理顺层级关系，使评论弹窗可叠加于播放页之上，或统一纳入连续的手势导航层级。
- [x] **[BUG-08] 隐私与安全隐患：登录页硬编码明文测试账号密码**
  - **问题位置**：[AccountScreens.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/account/AccountScreens.kt#L46-L47)
  - **修复方案**：立即清除代码中的明文手机号与密码，默认置空（或仅在 Debug 模式下从本地不入库的 `local.properties` / `BuildConfig` 读取）。

---

## 二、 商业级标准音乐 App 规范化改造建议（六大维度）

### 维度 1：功能完备性（Functionality）
- [x] **[FUNC-01] 播放状态与队列本地持久化（Persistence）**
  - 使用 `DataStore` / `Room` 保存当前播放列表 `List<SongItem>`、当前索引、`playMode` 及播放进度 `positionMs`。App 冷启动时自动恢复到 [MiniPlayerBar.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/MiniPlayerBar.kt)，点击即可续播。
- [x] **[FUNC-02] 边听边存与离线缓存引擎（Audio Caching）**
  - 在 [PlaybackService.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/service/PlaybackService.kt) 中为 ExoPlayer 配置 `CacheDataSource.Factory` + `LeastRecentlyUsedCacheEvictor`（支持在设置中心调节缓存上限 512MB ~ 10GB 及一键清理缓存）。
- [x] **[FUNC-03] 睡眠定时器（Sleep Timer）**
  - 支持固定时长倒计时（15 / 30 / 45 / 60 分钟、自定义）以及**「播完当前这首歌后停止」**模式，倒计时结束前 5 秒音量平滑淡出。
- [x] **[FUNC-04] 倍速与升降调调节（Speed & Pitch）**
  - 基于 ExoPlayer `PlaybackParameters(speed, pitch)`，提供 `0.5x ~ 2.0x` 步进调节，以及「锁定音高（变速不变调）/ 联动音高（黑胶慢放/Nightcore 效果）」切换。
- [x] **[FUNC-05] 补齐二级详情页浏览体验**
  - **排行榜详情页 & 精选电台详情页**：修改 [HomeScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/home/HomeScreen.kt#L446-L561)，点击榜单/电台卡片进入详情列表，点击右下角快捷播放按钮才直接盲播。
  - **歌手详情页分栏**：升级 [ArtistDetailScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/ArtistDetailScreen.kt)，新增「热门 50 首 / 专辑列表 / 歌手百科」Tab。
  - **音乐库闲置资产展示**：激活 [LibraryViewModel.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/viewmodel/LibraryViewModel.kt#L49-L59) 中已定义的 `followedArtists`（关注歌手）、`favoriteAlbums`（收藏专辑）、`purchasedSongs`（已购音乐）并在 [LibraryScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/LibraryScreen.kt) 展示。
- [x] **[FUNC-06] 落地的「设置中心（SettingsScreen）」**
  - 替换 [MainActivity.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/MainActivity.kt#L201-L203) 的 Toast 占位，提供：默认在线播放音质、外观主题模式、高潮跨轨淡入淡出开关、每日自动领 VIP 后台守护开关、音频缓存清理、关于与版本信息。

### 维度 2：交互体验（Interaction）
- [x] **[INT-01] MiniPlayerBar 水平滑动手势切歌与全场景常驻**
  - 在 [MiniPlayerBar.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/MiniPlayerBar.kt) 歌曲信息区域支持左右滑动切换上一首/下一首，带有阻尼回弹反馈。
  - 在搜索页（`search`）保留底部 `MiniPlayerBar`，方便边搜边听。
- [x] **[INT-02] 歌词视图交互时间尺（Interactive Lyric Scrubbing）**
  - 手动滑动歌词时，屏幕中央浮现水平时间基准线，高亮对准行的歌词起止时间，点击右侧「▶」直接 `seekTo` 该句起点并恢复自动滚动。
- [x] **[INT-03] 统一 Material 3 下拉刷新（PullToRefreshBox）**
  - 在 [HomeScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/home/HomeScreen.kt) 和 [LibraryScreen.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/library/LibraryScreen.kt) 引入标准下拉刷新手势，移除局促的顶部小刷新图标。
- [x] **[INT-04] 全局单曲菜单（Global Song Action Sheet）**
  - 统一所有页面的单曲 `⋮` 弹窗与长按手势菜单：下一首播放、加入队列、收藏到自建歌单、跳转歌手、跳转专辑、查看评论、歌曲信息（Hash / 音质 / 码率 / 文件大小）。

### 维度 3：动效设计（Motion & Animation）
- [x] **[MOT-01] KRC 逐字歌词流体渲染器重构（消除字重抖动 + 字内扫光 + 景深模糊）**
  - **问题位置**：[NowPlayingSheet.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/NowPlayingSheet.kt#L789-L835)
  - **改造要点**：
    1. **固定字重防抖**：移除动态切换 `FontWeight.Black` / `SemiBold` 的逻辑，统一字重，改用 `graphicsLayer { scaleX; scaleY; translationY }` 做单字轻盈上浮（`-2.dp`）与缩放（`1.05f`）。
    2. **字内卡拉 OK 线性渐变填充**：根据 `(currentPosition - word.startTime) / word.duration` 计算单字内部进度 $p \in [0, 1]$，通过 `Brush.horizontalGradient` 实现丝滑扫光，对长音字（`duration > 800ms`）叠加微弱呼吸辉光。
    3. **阶梯景深模糊**：根据行距 $|i - \text{current}|$ 动态计算透明度与模糊半径，营造 Apple Music / `md3Music` 级别的空間纵深感。
- [x] **[MOT-02] 唱片旋转相位连续性与拟物唱臂动画**
  - 将旋转角度状态提升至统一控制器，避免切换封面/歌词或展开播放页时角度归零。
  - 绘制拟物黑胶唱臂：播放时平滑旋入唱片边缘，暂停时抬起归位。
- [x] **[MOT-03] 共享元素与空间容器过渡（Shared / Container Element Transitions）**
  - 使用 Compose 容器缩放与透明过渡连接 `MiniPlayerBar` 与 `NowPlayingSheet` 以及全站二级路由页面切换。

### 维度 4：UI 视觉与色彩系统（UI & Color System）
- [x] **[UI-01] 专辑封面动态取色与流光背景（Palette Mesh Background）**
  - 切歌时异步提取当前封面的 `Dominant`、`Vibrant`、`MutedDark` 三原色，通过 `animateColorAsState`（`tween(800)`）平滑过渡，绘制双层缓慢呼吸的径向渐变流光背景。
- [x] **[UI-02] 玻璃拟态（Glassmorphism）层级规范**
  - 统一底部导航栏 [BottomNavBar.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/navigation/BottomNavBar.kt) 与 [MiniPlayerBar.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/MiniPlayerBar.kt) 的半透明磨砂质感、1px 高光描边（`Color.White.copy(alpha = 0.08f)`）与柔和投影。

### 维度 5：排版与布局规范（Typography & Layout）
- [x] **[TYP-01] 数字等宽特性（Tabular Figures `tnum`）**
  - 在 [Type.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/theme/Type.kt) 及所有时间戳、倒计时、曲目序号文本中启用 `fontFeatureSettings = "tnum"`，彻底消除每秒数字宽度变化引发的 UI 抖动。
- [x] **[TYP-02] 沉浸式 Edge-to-Edge 与 WindowInsets 标准适配**
  - 清理二级页面硬编码的 `padding(top = 48.dp)`，全面改用 `WindowInsets.statusBars`、`WindowInsets.navigationBars` 与底部播放条安全高度留白。
- [x] **[TYP-03] 正在播放曲目动态音波指示器**
  - 在 [SongItemRow.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/SongItemRow.kt) 中，当 `isPlaying == true` 时，将左侧序号替换为 3 根错落律动的音频柱动画，并将标题高亮为主题色。

### 维度 6：底层工程与并发质量（Architecture & Quality）
- [x] **[ARC-01] 异步化听歌等级存储**
  - 改造 [ListeningGradeManager.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/data/vip/ListeningGradeManager.kt)，将高频切歌时的 `SharedPreferences` 同步读写迁移至协程安全的 `DataStore` 或内存缓存 + 防抖落盘。

---

## 三、 酷狗概念版专属差异化创新功能（Innovation）

- [x] **[INNOV-01] 「副歌刷刷」高潮速听探索模式（Chorus / Climax Reel Mode）**
  - **技术支点**：复用项目在 [MusicRepository.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/data/kugou/MusicRepository.kt) 中已解析的酷狗高潮时间段（`climaxStartMs` ~ `climaxEndMs`）与 [AudioEffectManager.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/service/AudioEffectManager.kt) 的淡入淡出能力。
  - **产品形态**：开启「副歌速听」后，每首歌直接从 `climaxStartMs` 淡入起播，到达 `climaxEndMs` 自动跨轨淡出至下一首副歌（每首仅听最精华的 25~30 秒）；遇到心仪歌曲点击「听整首」即可解除截断。
- [x] **[INNOV-02] 「概念多态机芯」播放页皮肤（Vinyl / Transparent Cassette / Poster / Circle）**
  - **产品形态**：在播放页支持一键切换四种概念版标志性视觉形态：
    1. **流光黑胶机芯**：旋转黑胶 + 物理阻尼唱臂。
    2. **透明机械磁带机芯**：Canvas 绘制双轴磁带齿轮，左右磁带卷半径随播放进度 `[0% -> 100%]` 真实消长。
    3. **杂志大字海报模式**：全画幅高清封面，底部内嵌实时 KRC 逐字高亮大字报歌词与双语对照。
    4. **极简圆盘模式**：纯净圆形旋转专辑封面。
- [x] **[INNOV-03] JobScheduler「全自动静默养号守护」与概念版桌面小组件**
  - **产品形态**：
    - 利用 `JobScheduler` ([VipGuardianManager.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/service/VipGuardianManager.kt)) 注册周期后台任务，静默执行每日 VIP 签到 + 畅听领 VIP + 8 首听歌上报，无需每天手动点开福利中心。
    - 提供桌面小组件 ([ConceptMusicWidgetUpdater.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/service/ConceptMusicWidgetUpdater.kt))，展示当前曲目、逐行歌词、播放控制及「今日概念 VIP 已守护」角标。
- [x] **[INNOV-04] 「心流环境音混音舱」（Flow State Ambient Mixer）**
  - **产品形态**：在音效面板内提供可与主音乐叠加播放的程序化 DSP 环境白噪音层（雨夜窗台、黑胶底噪、林间篝火、深海潮汐），支持独立音量滑杆与一键场景预设 ([AmbientSoundManager.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/service/AmbientSoundManager.kt))。
- [x] **[INNOV-05] 系统级 KRC 逐字悬浮歌词与状态栏歌词广播**
  - **产品形态**：利用已有的 [KrcDecoder.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/data/kugou/KrcDecoder.kt) 毫秒级逐字数据，提供全局可拖拽 KRC 逐字流光歌词胶囊 ([FloatingLyricOverlay.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/ui/components/FloatingLyricOverlay.kt))，并兼容主流定制系统状态栏歌词广播协议 ([SystemLyricBridge.kt](file:///e:/vibe%20coding/kugougainianban/app/src/main/java/com/concept/music/service/SystemLyricBridge.kt))。

---

## 四、 分阶段执行路线图（Execution Roadmap）

### 阶段一：核心 BUG 清零与基础体验闭环（Phase 1） ✅ 已完成
1. 统一色彩系统与深浅色适配（解决 `[BUG-01]`）+ 清除硬编码敏感信息（`[BUG-08]`）。
2. 修复均衡器垂直滑杆旋转（`[BUG-02]`）与低版本背景模糊兼容（`[BUG-03]`）。
3. 打通播放页红心收藏同步（`[BUG-04]`）、全局单曲 `⋮` 操作面板与列表正在播放音波高亮（`[BUG-05]`、`[TYP-03]`）。
4. 修复歌词手动滑动防抢占与跳转时间尺（`[BUG-06]`、`[INT-02]`）及评论页弹窗层级（`[BUG-07]`）。

### 阶段二：动效跃迁与标准播放器能力补齐（Phase 2） ✅ 已完成
1. 重构 KRC 逐字歌词流体渲染器（`[MOT-01]`）+ 封面 Palette 动态流光背景（`[UI-01]`）+ 唱片连续旋转与唱臂（`[MOT-02]`）。
2. 实现 `MiniPlayerBar` 横滑切歌（`[INT-01]`）、下拉刷新（`[INT-03]`）与数字等宽字体 `tnum`（`[TYP-01]`）。
3. 落地播放队列持久化（`[FUNC-01]`）、边听边存缓存（`[FUNC-02]`）、睡眠定时器（`[FUNC-03]`）、倍速升降调（`[FUNC-04]`）、二级榜单/歌手专辑详情（`[FUNC-05]`）与设置中心（`[FUNC-06]`）。

### 阶段三：酷狗概念版灵魂创新落地（Phase 3） ✅ 已完成
1. 上线「副歌刷刷」高潮速听模式（`[INNOV-01]`）。
2. 上线「透明机械磁带 / 杂志大字海报 / 流光黑胶 / 极简圆盘」多态播放机芯（`[INNOV-02]`）。
3. 上线每日自动领 VIP 静默守护与概念版桌面小组件（`[INNOV-03]`）、「心流环境音混音舱」（`[INNOV-04]`）及「全局悬浮逐字歌词与状态栏歌词广播」（`[INNOV-05]`）。
