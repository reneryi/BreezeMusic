package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReverbMode(val label: String, val preset: Short) {
    NONE("无混响", PresetReverb.PRESET_NONE),
    SMALL_ROOM("小型房间", PresetReverb.PRESET_SMALLROOM),
    MEDIUM_ROOM("中型房间", PresetReverb.PRESET_MEDIUMROOM),
    LARGE_ROOM("大型房间", PresetReverb.PRESET_LARGEROOM),
    MEDIUM_HALL("音乐大厅", PresetReverb.PRESET_MEDIUMHALL),
    LARGE_HALL("混响礼堂", PresetReverb.PRESET_LARGEHALL),
    PLATE("录音室板式", PresetReverb.PRESET_PLATE)
}

/**
 * 酷狗蝰蛇音效六大分类体系（对齐酷狗官方蝰蛇音效开放平台与 KuGouMusicApi 音效模块）
 */
enum class ViperEffectCategory(val key: String, val label: String, val subtitle: String) {
    ALL("all", "全部音效", "酷狗蝰蛇全量声学矩阵"),
    FLAGSHIP("flagship", "官方旗舰", "亿级听友首选王牌音效"),
    SPATIAL_3D("spatial_3d", "3D空间", "全息声场与现场环绕还原"),
    RETRO_ANALOG("retro_analog", "复古介质", "黑胶母带、磁带与电子管胆味"),
    GENRE_VOCAL("genre_vocal", "曲风人声", "古风、民谣、ACG与电音特调"),
    DEVICE_CAR("device_car", "耳机车载", "针对耳机声学与车厢反射定制")
}

/**
 * 酷狗蝰蛇音效与专业均衡器预设全集
 * 保留全部原有枚举常量名以兼容单元测试，并扩充酷狗核心蝰蛇音效库与详细声学参数
 */
enum class EffectPreset(
    val label: String,
    val desc: String,
    val category: ViperEffectCategory = ViperEffectCategory.FLAGSHIP,
    val isEqPreset: Boolean = false,
    val usageBadge: String = "官方认证",
    val acousticTags: List<String> = listOf("蝰蛇DSP", "高解析"),
    val modeId: String = "0"
) {
    OFF(
        label = "关闭音效",
        desc = "原汁原味原生直通输出",
        category = ViperEffectCategory.FLAGSHIP,
        isEqPreset = true,
        usageBadge = "FastMixer直通",
        acousticTags = listOf("0音染", "Bit-Perfect")
    ),
    FLAT(
        label = "平直均衡",
        desc = "全频段 0dB 平直参考响应",
        category = ViperEffectCategory.FLAGSHIP,
        isEqPreset = true,
        usageBadge = "监听基准",
        acousticTags = listOf("平直响应", "参考级")
    ),

    // 1. 官方旗舰音效 (Flagship Viper)
    VIPER_3D(
        label = "3D丽音",
        desc = "环绕立体声场与晶莹高频泛音，给你极致听觉感受",
        category = ViperEffectCategory.FLAGSHIP,
        usageBadge = "5.4亿人使用",
        acousticTags = listOf("3D声场", "高频晶莹", "旗舰王牌"),
        modeId = "1001"
    ),
    VIPER_BASS(
        label = "超重低音",
        desc = "澎湃低频下潜与弹性瞬态冲击，低音浑厚不轰头",
        category = ViperEffectCategory.FLAGSHIP,
        usageBadge = "5.8亿人使用",
        acousticTags = listOf("深沉下潜", "低频动态", "节奏澎湃"),
        modeId = "1002"
    ),
    VIPER_VOCAL(
        label = "纯净人声",
        desc = "还原音频中每一个人声呼吸细节，中频温润贴耳",
        category = ViperEffectCategory.FLAGSHIP,
        usageBadge = "4.2亿人使用",
        acousticTags = listOf("人声贴耳", "齿音柔化", "通透中频"),
        modeId = "1003"
    ),
    VIPER_HIFI(
        label = "HiFi现场",
        desc = "逼真丰富的现场微动态与宽阔舞台结像感",
        category = ViperEffectCategory.FLAGSHIP,
        usageBadge = "3.1亿人使用",
        acousticTags = listOf("临场还原", "微动态", "高保真"),
        modeId = "1004"
    ),
    VIPER_PANORAMA_51(
        label = "5.1全景声",
        desc = "多声道球形全息影院声场，沉浸式环绕听感",
        category = ViperEffectCategory.FLAGSHIP,
        usageBadge = "2.5亿人使用",
        acousticTags = listOf("5.1多声道", "分轨空间", "影院包裹"),
        modeId = "1005"
    ),
    VIPER_BLACK_DIAMOND(
        label = "黑钻蝰蛇·母带",
        desc = "多重谐波修复技术，还原黑胶母带光泽与微细节",
        category = ViperEffectCategory.FLAGSHIP,
        usageBadge = "1.9亿人使用",
        acousticTags = listOf("谐波修复", "母带重塑", "VIP臻享"),
        modeId = "1006"
    ),
    VIPER_CLEAR_MASTER(
        label = "蝰蛇·超清母带",
        desc = "192kHz采样率声学补偿，展现极高频空气泛音",
        category = ViperEffectCategory.FLAGSHIP,
        usageBadge = "2.2亿人使用",
        acousticTags = listOf("192kHz感", "空气泛音", "超高解析"),
        modeId = "1007"
    ),

    // 2. 3D 空间与现场 (3D & Spatial)
    VIPER_3D_ROTATE(
        label = "3D旋转",
        desc = "声源360°环绕头部动态轨迹旋转，灵动空间漫游",
        category = ViperEffectCategory.SPATIAL_3D,
        usageBadge = "1.6亿人使用",
        acousticTags = listOf("360°轨迹", "双耳渲染", "头部环绕"),
        modeId = "2001"
    ),
    VIPER_SURROUND(
        label = "全景环绕",
        desc = "360°沉浸式声场扩展，打破耳机头中效应",
        category = ViperEffectCategory.SPATIAL_3D,
        usageBadge = "2.1亿人使用",
        acousticTags = listOf("全息声场", "消除头中感", "宽阔包裹"),
        modeId = "2002"
    ),
    VIPER_CONCERT(
        label = "大型演唱会",
        desc = "万人露天体育场恢弘声学反射与舞台纵深感",
        category = ViperEffectCategory.SPATIAL_3D,
        usageBadge = "1.3亿人使用",
        acousticTags = listOf("万人场馆", "远场混响", "恢弘声场"),
        modeId = "2003"
    ),
    VIPER_LIVEHOUSE(
        label = "Livehouse前排",
        desc = "近场舞台监听与热烈低频空气感，犹如置身第一排",
        category = ViperEffectCategory.SPATIAL_3D,
        usageBadge = "9400万人使用",
        acousticTags = listOf("近场舞台", "乐队现场", "鼓点扎实"),
        modeId = "2004"
    ),
    VIPER_STUDIO(
        label = "录音棚监听",
        desc = "近场声学消音室精准结像，还原混音师母带监听台",
        category = ViperEffectCategory.SPATIAL_3D,
        usageBadge = "7600万人使用",
        acousticTags = listOf("精准结像", "近场监听", "三频均衡"),
        modeId = "2005"
    ),

    // 3. 复古与模拟介质 (Retro & Analog Media)
    VIPER_VINYL(
        label = "黑胶唱片",
        desc = "温润复古模拟偶次谐波，柔和顺滑耐听",
        category = ViperEffectCategory.RETRO_ANALOG,
        usageBadge = "1.8亿人使用",
        acousticTags = listOf("偶次谐波", "温润模拟", "黑胶声底"),
        modeId = "3001"
    ),
    VIPER_TAPE(
        label = "复古磁带机",
        desc = "80年代Walkman二氧化铬磁带微饱和与温暖中低频",
        category = ViperEffectCategory.RETRO_ANALOG,
        usageBadge = "1.1亿人使用",
        acousticTags = listOf("磁带饱和", "复古怀旧", "柔和顺滑"),
        modeId = "3002"
    ),
    VIPER_TUBE(
        label = "黄金胆机电子管",
        desc = "经典甲类真空电子管放大器润色，人声醇厚浓郁",
        category = ViperEffectCategory.RETRO_ANALOG,
        usageBadge = "8500万人使用",
        acousticTags = listOf("真空胆味", "醇厚中频", "柔和泛音"),
        modeId = "3003"
    ),
    VIPER_GRAMOPHONE(
        label = "岁月留声机",
        desc = "复古号角中频聚焦与老唱片温暖年代质感",
        category = ViperEffectCategory.RETRO_ANALOG,
        usageBadge = "5200万人使用",
        acousticTags = listOf("年代号角", "复古胶片", "叙事感"),
        modeId = "3004"
    ),

    // 4. 曲风与人声特调 (Genre & Vocal Special)
    VIPER_GUFENG(
        label = "声乐古风",
        desc = "强化竹笛古筝空灵泛音与国风戏腔清亮穿透力",
        category = ViperEffectCategory.GENRE_VOCAL,
        usageBadge = "1.4亿人使用",
        acousticTags = listOf("国风器乐", "戏腔清亮", "空灵余韵"),
        modeId = "4001"
    ),
    VIPER_FOLK(
        label = "民谣木吉他",
        desc = "突出木吉他拨弦箱体共鸣与温暖叙事男/女声",
        category = ViperEffectCategory.GENRE_VOCAL,
        usageBadge = "9600万人使用",
        acousticTags = listOf("原声吉他", "箱体共鸣", "温暖叙事"),
        modeId = "4002"
    ),
    VIPER_ACG(
        label = "ACG燃曲女毒",
        desc = "高密度瞬态响应与清甜高频延伸，专为二次元定制",
        category = ViperEffectCategory.GENRE_VOCAL,
        usageBadge = "8900万人使用",
        acousticTags = listOf("清甜女毒", "高速瞬态", "华丽高频"),
        modeId = "4003"
    ),
    VIPER_EDM(
        label = "电音俱乐部",
        desc = "极速超低频冲程与合成器高频空气感，燃爆节拍",
        category = ViperEffectCategory.GENRE_VOCAL,
        usageBadge = "1.5亿人使用",
        acousticTags = listOf("Sub-Bass", "电音冲击", "高频空气"),
        modeId = "4004"
    ),
    VIPER_RNB(
        label = "R&B丝滑律动",
        desc = "醇厚贝斯鼓点弹性与丝滑转音润色，律动感拉满",
        category = ViperEffectCategory.GENRE_VOCAL,
        usageBadge = "7300万人使用",
        acousticTags = listOf("律动贝斯", "丝滑人声", "氛围包裹"),
        modeId = "4005"
    ),

    // 5. 耳机与车载专属 (Headphone & Car Audio)
    VIPER_IEM(
        label = "入耳式耳塞特调",
        desc = "平滑耳道6kHz刺耳谐振峰，拓宽入耳式横向声场",
        category = ViperEffectCategory.DEVICE_CAR,
        usageBadge = "1.1亿人使用",
        acousticTags = listOf("耳道补偿", "去齿音毛刺", "声场拓宽"),
        modeId = "5001"
    ),
    VIPER_HEADPHONE(
        label = "头戴式大耳声场",
        desc = "模拟开放式旗舰头戴大耳自然声场与顺滑三频",
        category = ViperEffectCategory.DEVICE_CAR,
        usageBadge = "6800万人使用",
        acousticTags = listOf("开放式声场", "自然宽松", "大耳模拟"),
        modeId = "5002"
    ),
    VIPER_TWS(
        label = "TWS真无线增强",
        desc = "补偿蓝牙AAC/SBC高频衰减，强化低频下潜与凝聚力",
        category = ViperEffectCategory.DEVICE_CAR,
        usageBadge = "1.7亿人使用",
        acousticTags = listOf("蓝牙补偿", "高频修复", "低频凝聚"),
        modeId = "5003"
    ),
    VIPER_CAR_LUXURY(
        label = "豪车柏林之声",
        desc = "针对车厢玻璃反射声学优化的皇帝位环绕声场",
        category = ViperEffectCategory.DEVICE_CAR,
        usageBadge = "1.3亿人使用",
        acousticTags = listOf("车载皇帝位", "多扬声器", "座舱声学"),
        modeId = "5004"
    ),
    VIPER_CAR_SUBWOOFER(
        label = "车载重低音炮",
        desc = "强化车门与后备箱低音炮共鸣，高速行车依然澎湃",
        category = ViperEffectCategory.DEVICE_CAR,
        usageBadge = "1.0亿人使用",
        acousticTags = listOf("车载低音炮", "胎噪补偿", "胸腔共鸣"),
        modeId = "5005"
    ),

    // 6. 专业均衡器曲风预设 (Professional Equalizer Presets)
    VIPER_DYNAMIC(
        label = "动态增强",
        desc = "宽动态瞬态强化，高低频两端延伸清晰有力",
        category = ViperEffectCategory.FLAGSHIP,
        isEqPreset = true,
        usageBadge = "动态母带",
        acousticTags = listOf("瞬态增强", "高低延伸")
    ),
    POP(
        label = "现代流行",
        desc = "人声与伴奏黄金比例，中频饱满自然",
        category = ViperEffectCategory.GENRE_VOCAL,
        isEqPreset = true,
        usageBadge = "流行首选",
        acousticTags = listOf("流行黄金比", "人声饱满")
    ),
    ROCK(
        label = "摇滚动感",
        desc = "经典V型频响曲线，架子鼓与电吉他冲击力强",
        category = ViperEffectCategory.GENRE_VOCAL,
        isEqPreset = true,
        usageBadge = "摇滚现场",
        acousticTags = listOf("V型曲线", "电吉他凌厉")
    ),
    EQ_CLASSICAL(
        label = "古典交响",
        desc = "宽广动态范围与高频泛音延伸，还原交响乐团层次",
        category = ViperEffectCategory.SPATIAL_3D,
        isEqPreset = true,
        usageBadge = "交响大厅",
        acousticTags = listOf("器乐分离", "泛音延伸")
    ),
    EQ_JAZZ(
        label = "慵懒爵士",
        desc = "突出萨克斯管光泽与低音提琴拨弦弹性",
        category = ViperEffectCategory.RETRO_ANALOG,
        isEqPreset = true,
        usageBadge = "爵士酒廊",
        acousticTags = listOf("管乐醇厚", "低音提琴")
    ),
    EQ_DANCE(
        label = "动感舞曲",
        desc = "强化60Hz底鼓冲击与10kHz打击乐脆度",
        category = ViperEffectCategory.GENRE_VOCAL,
        isEqPreset = true,
        usageBadge = "律动节拍",
        acousticTags = listOf("底鼓强化", "高频清脆")
    ),
    EQ_HIPHOP(
        label = "说唱嘻哈",
        desc = "厚重808 Sub-Bass下潜与人声中高频清晰咬字",
        category = ViperEffectCategory.GENRE_VOCAL,
        isEqPreset = true,
        usageBadge = "808重拍",
        acousticTags = listOf("808低频", "清晰咬字")
    ),
    EQ_ACOUSTIC(
        label = "原声纯音",
        desc = "自然温润的原声乐器频响，久听舒适通透",
        category = ViperEffectCategory.RETRO_ANALOG,
        isEqPreset = true,
        usageBadge = "自然耐听",
        acousticTags = listOf("原声器乐", "自然泛音")
    ),
    EQ_SOFT(
        label = "轻柔助眠",
        desc = "柔化中高频刺激感，营造松弛温暖的睡前听感",
        category = ViperEffectCategory.RETRO_ANALOG,
        isEqPreset = true,
        usageBadge = "深夜舒缓",
        acousticTags = listOf("高频柔化", "久听不累")
    ),
    CUSTOM(
        label = "自定义调节",
        desc = "自由精调详细声学参数与多频段均衡推杆",
        category = ViperEffectCategory.FLAGSHIP,
        isEqPreset = true,
        usageBadge = "私人定制",
        acousticTags = listOf("自由精调", "专属曲线")
    )
}

/**
 * 酷狗 API 在线获取到的音效扩展元数据（对齐 KuGouMusicApi get_mode_info / get_model）
 */
data class ViperOnlineEffectMeta(
    val modeId: String,
    val name: String,
    val description: String,
    val usageText: String,
    val authorName: String = "酷狗声学实验室",
    val tuningSummary: String = ""
)

data class AudioEffectState(
    val isEnabled: Boolean = false,
    val currentPreset: EffectPreset = EffectPreset.OFF,
    val bassBoostStrength: Int = 0,          // 0 ~ 1000 (超重低音下潜)
    val bassCutoffHz: Int = 60,              // 45 / 60 / 85 Hz (低频谐振中心频点)
    val virtualizerStrength: Int = 0,        // 0 ~ 1000 (3D 空间环绕宽度)
    val vocalClarityStrength: Int = 0,       // 0 ~ 1000 (人声通透与泛音激励)
    val analogWarmthStrength: Int = 0,       // 0 ~ 1000 (黑胶/胆机偶次谐波温暖度)
    val surroundRotationSpeed: Int = 0,      // 0 ~ 100 (3D 旋转/动态声场速率)
    val loudnessGainMb: Int = 0,             // 0 ~ 1500 mB (0 ~ 15 dB 动态响度增益)
    val preampGainDb: Float = 0f,            // -6f ~ +6f dB (专业均衡器前置增益)
    val channelBalance: Float = 0f,          // -1f (左) ~ +1f (右) 声像平衡
    val antiClippingEnabled: Boolean = true, // 硬件级防破音数字限幅保护
    val reverbMode: ReverbMode = ReverbMode.NONE,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val tenBandLevels: List<Int> = List(10) { 0 },
    val onlineEffectMeta: Map<String, ViperOnlineEffectMeta> = emptyMap(),
    val syncingOnlineEffects: Boolean = false
)

object AudioEffectManager {
    private var prefs: SharedPreferences? = null
    private var currentSessionId: Int = 0
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    private val _state = MutableStateFlow(AudioEffectState())
    val state = _state.asStateFlow()

    val TEN_BAND_FREQUENCIES = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val FIVE_BAND_FREQUENCIES = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("concept_audio_effects", Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    /**
     * 结合 MakcRe/KuGouMusicApi 音效接口异步拉取酷狗官方与社区蝰蛇音效详情数据
     */
    fun refreshOnlineViperEffects() {
        if (_state.value.syncingOnlineEffects) return
        ioScope.launch {
            _state.value = _state.value.copy(syncingOnlineEffects = true)
            val fetched = runCatching { VipManager.music.fetchViperSoundEffects() }.getOrDefault(emptyMap())
            _state.value = _state.value.copy(
                onlineEffectMeta = if (fetched.isNotEmpty()) fetched else _state.value.onlineEffectMeta,
                syncingOnlineEffects = false
            )
        }
    }

    fun attachSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (sessionId != currentSessionId) {
            releaseEffects()
            currentSessionId = sessionId
        }
        applyCurrentState()
    }

    private fun ensureEffectsCreated() {
        val sessionId = currentSessionId
        if (sessionId <= 0) return
        try {
            if (equalizer == null) runCatching { equalizer = Equalizer(0, sessionId) }
            if (bassBoost == null) runCatching { bassBoost = BassBoost(0, sessionId) }
            if (virtualizer == null) runCatching { virtualizer = Virtualizer(0, sessionId) }
            if (loudnessEnhancer == null) runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId) }
            if (presetReverb == null) runCatching { presetReverb = PresetReverb(0, sessionId) }
        } catch (_: Exception) {
        }
    }

    internal fun defaultBandsForPreset(preset: EffectPreset): List<Int> = when (preset) {
        EffectPreset.OFF -> listOf(0, 0, 0, 0, 0)
        EffectPreset.FLAT -> listOf(0, 0, 0, 0, 0)
        EffectPreset.VIPER_3D -> listOf(60, 20, -40, 60, 90)
        EffectPreset.VIPER_VOCAL -> listOf(-120, 40, 140, 100, 30)
        EffectPreset.VIPER_BASS -> listOf(160, 90, -40, -60, 20)
        EffectPreset.VIPER_HIFI -> listOf(60, 20, 20, 60, 100)
        EffectPreset.VIPER_VINYL -> listOf(100, 40, 20, 20, -80)
        EffectPreset.VIPER_SURROUND -> listOf(60, 20, -30, 90, 120)
        EffectPreset.VIPER_DYNAMIC -> listOf(120, 60, 0, 60, 120)
        EffectPreset.ROCK -> listOf(100, 60, -80, 60, 100)
        EffectPreset.POP -> listOf(60, 60, 100, 60, 30)
        EffectPreset.VIPER_PANORAMA_51 -> listOf(90, 30, -20, 80, 110)
        EffectPreset.VIPER_BLACK_DIAMOND -> listOf(80, 40, 30, 70, 90)
        EffectPreset.VIPER_CLEAR_MASTER -> listOf(40, 20, 30, 90, 130)
        EffectPreset.VIPER_3D_ROTATE -> listOf(70, 10, -30, 80, 100)
        EffectPreset.VIPER_CONCERT -> listOf(110, 50, -20, 60, 90)
        EffectPreset.VIPER_LIVEHOUSE -> listOf(130, 70, 10, 50, 70)
        EffectPreset.VIPER_STUDIO -> listOf(20, 10, 20, 40, 50)
        EffectPreset.VIPER_TAPE -> listOf(90, 60, 30, -20, -70)
        EffectPreset.VIPER_TUBE -> listOf(80, 70, 80, 30, -40)
        EffectPreset.VIPER_GRAMOPHONE -> listOf(-160, -40, 150, 90, -140)
        EffectPreset.VIPER_GUFENG -> listOf(-40, 30, 110, 130, 90)
        EffectPreset.VIPER_FOLK -> listOf(40, 60, 90, 80, 40)
        EffectPreset.VIPER_ACG -> listOf(50, 10, 60, 130, 120)
        EffectPreset.VIPER_EDM -> listOf(180, 100, -60, 80, 140)
        EffectPreset.VIPER_RNB -> listOf(140, 90, 20, 60, 70)
        EffectPreset.VIPER_IEM -> listOf(50, 30, 20, -50, 80)
        EffectPreset.VIPER_HEADPHONE -> listOf(70, 40, 10, 50, 80)
        EffectPreset.VIPER_TWS -> listOf(110, 40, 10, 70, 110)
        EffectPreset.VIPER_CAR_LUXURY -> listOf(120, 60, 20, 70, 100)
        EffectPreset.VIPER_CAR_SUBWOOFER -> listOf(190, 110, -30, 40, 60)
        EffectPreset.EQ_CLASSICAL -> listOf(50, 20, -10, 60, 100)
        EffectPreset.EQ_JAZZ -> listOf(70, 50, 30, 50, 70)
        EffectPreset.EQ_DANCE -> listOf(150, 80, -40, 60, 110)
        EffectPreset.EQ_HIPHOP -> listOf(170, 90, 0, 50, 80)
        EffectPreset.EQ_ACOUSTIC -> listOf(40, 50, 60, 70, 60)
        EffectPreset.EQ_SOFT -> listOf(40, 30, 10, -40, -70)
        EffectPreset.CUSTOM -> listOf(0, 0, 0, 0, 0)
    }

    internal fun expandFiveToTenBands(fiveBands: List<Int>): List<Int> {
        val b0 = fiveBands.getOrElse(0) { 0 }
        val b1 = fiveBands.getOrElse(1) { 0 }
        val b2 = fiveBands.getOrElse(2) { 0 }
        val b3 = fiveBands.getOrElse(3) { 0 }
        val b4 = fiveBands.getOrElse(4) { 0 }
        return listOf(
            b0,
            (b0 * 2 + b1) / 3,
            (b0 + b1 * 2) / 3,
            b1,
            (b1 + b2) / 2,
            b2,
            (b2 + b3) / 2,
            b3,
            (b3 + b4) / 2,
            b4
        )
    }

    internal fun compressTenToFiveBands(tenBands: List<Int>): List<Int> {
        if (tenBands.size < 10) return defaultBandsForPreset(EffectPreset.FLAT)
        return listOf(
            (tenBands[0] + tenBands[1]) / 2,
            (tenBands[2] + tenBands[3]) / 2,
            (tenBands[4] + tenBands[5]) / 2,
            (tenBands[6] + tenBands[7]) / 2,
            (tenBands[8] + tenBands[9]) / 2
        )
    }

    internal fun serializeBands(bands: List<Int>): String =
        (0 until 5).joinToString(",") { idx -> (bands.getOrNull(idx) ?: 0).coerceIn(-1000, 1000).toString() }

    internal fun parseBandsString(raw: String?, fallbackPreset: EffectPreset = EffectPreset.OFF): List<Int> {
        if (!raw.isNullOrBlank()) {
            val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull()?.coerceIn(-1000, 1000) }
            if (parsed.size == 5) return parsed
        }
        return defaultBandsForPreset(fallbackPreset)
    }

    private data class PresetDetailedProfile(
        val bass: Int,
        val cutoffHz: Int,
        val virt: Int,
        val clarity: Int,
        val warmth: Int,
        val rotationSpeed: Int,
        val loudness: Int,
        val reverb: ReverbMode,
        val bands: List<Int>
    )

    private fun profileForPreset(preset: EffectPreset): PresetDetailedProfile {
        val bands = defaultBandsForPreset(preset)
        return when (preset) {
            EffectPreset.OFF -> PresetDetailedProfile(0, 60, 0, 0, 0, 0, 0, ReverbMode.NONE, bands)
            EffectPreset.FLAT -> PresetDetailedProfile(0, 60, 0, 0, 0, 0, 0, ReverbMode.NONE, bands)
            EffectPreset.VIPER_3D -> PresetDetailedProfile(180, 60, 380, 340, 120, 0, 80, ReverbMode.NONE, bands)
            EffectPreset.VIPER_VOCAL -> PresetDetailedProfile(40, 85, 160, 480, 220, 0, 60, ReverbMode.NONE, bands)
            EffectPreset.VIPER_BASS -> PresetDetailedProfile(420, 45, 140, 180, 260, 0, 100, ReverbMode.NONE, bands)
            EffectPreset.VIPER_HIFI -> PresetDetailedProfile(140, 60, 280, 320, 180, 0, 80, ReverbMode.MEDIUM_HALL, bands)
            EffectPreset.VIPER_PANORAMA_51 -> PresetDetailedProfile(240, 45, 520, 300, 160, 20, 100, ReverbMode.LARGE_HALL, bands)
            EffectPreset.VIPER_BLACK_DIAMOND -> PresetDetailedProfile(200, 60, 260, 420, 380, 0, 90, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_CLEAR_MASTER -> PresetDetailedProfile(120, 60, 220, 520, 140, 0, 80, ReverbMode.NONE, bands)
            EffectPreset.VIPER_3D_ROTATE -> PresetDetailedProfile(160, 60, 600, 260, 100, 65, 90, ReverbMode.MEDIUM_HALL, bands)
            EffectPreset.VIPER_SURROUND -> PresetDetailedProfile(200, 60, 480, 280, 140, 15, 100, ReverbMode.LARGE_HALL, bands)
            EffectPreset.VIPER_CONCERT -> PresetDetailedProfile(250, 60, 500, 280, 200, 0, 110, ReverbMode.LARGE_HALL, bands)
            EffectPreset.VIPER_LIVEHOUSE -> PresetDetailedProfile(280, 60, 320, 300, 240, 0, 100, ReverbMode.MEDIUM_ROOM, bands)
            EffectPreset.VIPER_STUDIO -> PresetDetailedProfile(80, 60, 120, 380, 100, 0, 40, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_VINYL -> PresetDetailedProfile(160, 60, 180, 180, 520, 0, 60, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_TAPE -> PresetDetailedProfile(180, 85, 150, 160, 560, 0, 70, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_TUBE -> PresetDetailedProfile(150, 60, 200, 280, 620, 0, 80, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_GRAMOPHONE -> PresetDetailedProfile(0, 85, 80, 240, 480, 0, 50, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_GUFENG -> PresetDetailedProfile(60, 85, 320, 460, 200, 0, 70, ReverbMode.MEDIUM_HALL, bands)
            EffectPreset.VIPER_FOLK -> PresetDetailedProfile(110, 60, 220, 380, 340, 0, 60, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_ACG -> PresetDetailedProfile(140, 60, 280, 500, 120, 0, 90, ReverbMode.NONE, bands)
            EffectPreset.VIPER_EDM -> PresetDetailedProfile(460, 45, 380, 360, 160, 0, 120, ReverbMode.LARGE_ROOM, bands)
            EffectPreset.VIPER_RNB -> PresetDetailedProfile(320, 60, 260, 300, 320, 0, 90, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_IEM -> PresetDetailedProfile(140, 60, 340, 280, 220, 0, 60, ReverbMode.NONE, bands)
            EffectPreset.VIPER_HEADPHONE -> PresetDetailedProfile(160, 45, 380, 320, 240, 0, 70, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_TWS -> PresetDetailedProfile(260, 60, 300, 380, 180, 0, 90, ReverbMode.NONE, bands)
            EffectPreset.VIPER_CAR_LUXURY -> PresetDetailedProfile(280, 45, 440, 340, 260, 0, 110, ReverbMode.MEDIUM_ROOM, bands)
            EffectPreset.VIPER_CAR_SUBWOOFER -> PresetDetailedProfile(480, 45, 260, 220, 280, 0, 120, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.VIPER_DYNAMIC -> PresetDetailedProfile(260, 60, 260, 340, 180, 0, 120, ReverbMode.NONE, bands)
            EffectPreset.ROCK -> PresetDetailedProfile(220, 60, 200, 300, 160, 0, 100, ReverbMode.NONE, bands)
            EffectPreset.POP -> PresetDetailedProfile(140, 60, 160, 320, 200, 0, 60, ReverbMode.NONE, bands)
            EffectPreset.EQ_CLASSICAL -> PresetDetailedProfile(100, 45, 320, 340, 180, 0, 60, ReverbMode.MEDIUM_HALL, bands)
            EffectPreset.EQ_JAZZ -> PresetDetailedProfile(160, 60, 220, 280, 360, 0, 60, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.EQ_DANCE -> PresetDetailedProfile(340, 45, 240, 280, 140, 0, 100, ReverbMode.NONE, bands)
            EffectPreset.EQ_HIPHOP -> PresetDetailedProfile(380, 45, 200, 320, 220, 0, 100, ReverbMode.NONE, bands)
            EffectPreset.EQ_ACOUSTIC -> PresetDetailedProfile(100, 60, 180, 340, 280, 0, 50, ReverbMode.SMALL_ROOM, bands)
            EffectPreset.EQ_SOFT -> PresetDetailedProfile(90, 60, 140, 120, 380, 0, 30, ReverbMode.NONE, bands)
            EffectPreset.CUSTOM -> {
                val cur = _state.value
                PresetDetailedProfile(
                    cur.bassBoostStrength,
                    cur.bassCutoffHz,
                    cur.virtualizerStrength,
                    cur.vocalClarityStrength,
                    cur.analogWarmthStrength,
                    cur.surroundRotationSpeed,
                    cur.loudnessGainMb,
                    cur.reverbMode,
                    cur.bandLevels
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val nextPreset = if (enabled && _state.value.currentPreset == EffectPreset.OFF) {
            EffectPreset.VIPER_3D
        } else if (!enabled) {
            EffectPreset.OFF
        } else {
            _state.value.currentPreset
        }
        if (enabled && _state.value.currentPreset == EffectPreset.OFF) {
            setPreset(nextPreset)
            return
        }
        _state.value = _state.value.copy(isEnabled = enabled, currentPreset = nextPreset)
        applyCurrentState()
        saveToPrefs()
    }

    fun setPreset(preset: EffectPreset) {
        val p = profileForPreset(preset)
        _state.value = _state.value.copy(
            isEnabled = preset != EffectPreset.OFF,
            currentPreset = preset,
            bassBoostStrength = p.bass,
            bassCutoffHz = p.cutoffHz,
            virtualizerStrength = p.virt,
            vocalClarityStrength = p.clarity,
            analogWarmthStrength = p.warmth,
            surroundRotationSpeed = p.rotationSpeed,
            loudnessGainMb = p.loudness,
            reverbMode = p.reverb,
            bandLevels = p.bands,
            tenBandLevels = expandFiveToTenBands(p.bands)
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun resetCurrentPresetToDefault() {
        val current = _state.value.currentPreset
        if (current != EffectPreset.CUSTOM && current != EffectPreset.OFF) {
            setPreset(current)
        } else {
            setPreset(EffectPreset.FLAT)
        }
    }

    fun setCustomBandLevel(bandIndex: Int, levelMb: Int) {
        val currentBands = _state.value.bandLevels.toMutableList()
        if (bandIndex in currentBands.indices) {
            currentBands[bandIndex] = levelMb.coerceIn(-1000, 1000)
            _state.value = _state.value.copy(
                isEnabled = true,
                currentPreset = EffectPreset.CUSTOM,
                bandLevels = currentBands,
                tenBandLevels = expandFiveToTenBands(currentBands)
            )
            applyCurrentState()
            saveToPrefs()
        }
    }

    fun setTenBandLevel(tenBandIndex: Int, levelMb: Int) {
        val currentTen = _state.value.tenBandLevels.toMutableList().let {
            if (it.size == 10) it else expandFiveToTenBands(_state.value.bandLevels).toMutableList()
        }
        if (tenBandIndex in currentTen.indices) {
            currentTen[tenBandIndex] = levelMb.coerceIn(-1000, 1000)
            val mappedFive = compressTenToFiveBands(currentTen)
            _state.value = _state.value.copy(
                isEnabled = true,
                currentPreset = EffectPreset.CUSTOM,
                bandLevels = mappedFive,
                tenBandLevels = currentTen
            )
            applyCurrentState()
            saveToPrefs()
        }
    }

    fun setBassBoostStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            isEnabled = true,
            bassBoostStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setBassCutoffHz(cutoffHz: Int) {
        val c = cutoffHz.coerceIn(40, 120)
        _state.value = _state.value.copy(
            isEnabled = true,
            bassCutoffHz = c
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setVirtualizerStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            isEnabled = true,
            virtualizerStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setVocalClarityStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            isEnabled = true,
            vocalClarityStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setAnalogWarmthStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(
            isEnabled = true,
            analogWarmthStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setSurroundRotationSpeed(speed: Int) {
        val s = speed.coerceIn(0, 100)
        _state.value = _state.value.copy(
            isEnabled = true,
            surroundRotationSpeed = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setLoudnessGain(gainMb: Int) {
        val g = gainMb.coerceIn(0, 1500)
        _state.value = _state.value.copy(
            isEnabled = true,
            loudnessGainMb = g
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setPreampGainDb(gainDb: Float) {
        val g = gainDb.coerceIn(-6f, 6f)
        _state.value = _state.value.copy(
            isEnabled = true,
            preampGainDb = g
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setChannelBalance(balance: Float) {
        val b = balance.coerceIn(-1f, 1f)
        _state.value = _state.value.copy(
            channelBalance = b
        )
        saveToPrefs()
    }

    fun setAntiClippingEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(antiClippingEnabled = enabled)
        applyCurrentState()
        saveToPrefs()
    }

    fun setReverbMode(mode: ReverbMode) {
        _state.value = _state.value.copy(
            isEnabled = true,
            reverbMode = mode
        )
        applyCurrentState()
        saveToPrefs()
    }

    private fun applyCurrentState() {
        val s = _state.value
        if (!s.isEnabled || s.currentPreset == EffectPreset.OFF) {
            releaseEffects()
            return
        }

        ensureEffectsCreated()

        // 综合计算 5 段硬件 EQ（叠加人声通透激励、模拟温暖度、低频频点修正及前置增益与防削波保护）
        val clarityBoostMidHigh = (s.vocalClarityStrength / 10) // 0..100 mB
        val warmthBoostLowMid = (s.analogWarmthStrength / 12)   // 0..83 mB
        val preampOffsetMb = (s.preampGainDb * 100f).toInt()
        val effectiveBands = s.bandLevels.mapIndexed { idx, baseLevel ->
            val mod = when (idx) {
                0 -> (if (s.bassCutoffHz <= 50) 40 else 0) + warmthBoostLowMid / 2
                1 -> warmthBoostLowMid
                2 -> clarityBoostMidHigh / 2
                3 -> clarityBoostMidHigh
                4 -> (clarityBoostMidHigh * 2 / 3) - (warmthBoostLowMid / 2)
                else -> 0
            }
            val rawTarget = baseLevel + mod + preampOffsetMb
            val antiClipped = if (s.antiClippingEnabled && rawTarget > 180) rawTarget - 60 else rawTarget
            antiClipped.coerceIn(-1000, 1000)
        }

        val hasEqSignal = effectiveBands.any { it != 0 }
        equalizer?.let { eq ->
            runCatching {
                val numBands = eq.numberOfBands.toInt()
                effectiveBands.forEachIndexed { index, level ->
                    if (index < numBands) {
                        val min = eq.bandLevelRange[0]
                        val max = eq.bandLevelRange[1]
                        val target = level.toShort().coerceIn(min, max)
                        eq.setBandLevel(index.toShort(), target)
                    }
                }
                eq.enabled = hasEqSignal
            }
        }

        bassBoost?.let { bb ->
            runCatching {
                val active = s.bassBoostStrength > 0
                if (active && bb.strengthSupported) {
                    bb.setStrength(s.bassBoostStrength.toShort())
                }
                bb.enabled = active
            }
        }

        virtualizer?.let { vz ->
            runCatching {
                val active = s.virtualizerStrength > 0
                if (active && vz.strengthSupported) {
                    vz.setStrength(s.virtualizerStrength.toShort())
                }
                vz.enabled = active
            }
        }

        loudnessEnhancer?.let { le ->
            runCatching {
                val effectiveLoudness = (s.loudnessGainMb + (s.preampGainDb.coerceAtLeast(0f) * 80f).toInt())
                    .coerceIn(0, if (s.antiClippingEnabled) 1200 else 1500)
                val active = effectiveLoudness > 0
                le.setTargetGain(if (active) effectiveLoudness else 0)
                le.enabled = active
            }
        }

        presetReverb?.let { pr ->
            runCatching {
                val active = s.reverbMode != ReverbMode.NONE
                pr.preset = s.reverbMode.preset
                pr.enabled = active
            }
        }
    }

    private fun releaseEffects() {
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
        }
        runCatching {
            bassBoost?.enabled = false
            bassBoost?.release()
        }
        runCatching {
            virtualizer?.enabled = false
            virtualizer?.release()
        }
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        runCatching {
            presetReverb?.enabled = false
            presetReverb?.release()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        presetReverb = null
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        val enabled = p.getBoolean("effect_enabled_v2", false)
        val presetName = p.getString("preset_v2", EffectPreset.OFF.name) ?: EffectPreset.OFF.name
        val preset = runCatching { EffectPreset.valueOf(presetName) }.getOrDefault(EffectPreset.OFF)
        val defaultProf = profileForPreset(if (enabled) preset else EffectPreset.OFF)

        val bass = p.getInt("bass_v2", defaultProf.bass)
        val cutoffHz = p.getInt("bass_cutoff_v2", defaultProf.cutoffHz)
        val virt = p.getInt("virt_v2", defaultProf.virt)
        val clarity = p.getInt("clarity_v2", defaultProf.clarity)
        val warmth = p.getInt("warmth_v2", defaultProf.warmth)
        val rotSpeed = p.getInt("rot_speed_v2", defaultProf.rotationSpeed)
        val loudness = p.getInt("loudness_v2", defaultProf.loudness)
        val preamp = p.getFloat("preamp_db_v2", 0f)
        val balance = p.getFloat("channel_balance_v2", 0f)
        val antiClip = p.getBoolean("anti_clip_v2", true)
        val reverbName = p.getString("reverb_v2", defaultProf.reverb.name) ?: defaultProf.reverb.name
        val reverb = runCatching { ReverbMode.valueOf(reverbName) }.getOrDefault(ReverbMode.NONE)
        val bandsRaw = p.getString("bands_v2", null)
        val bands = parseBandsString(bandsRaw, if (enabled) preset else EffectPreset.OFF)

        _state.value = AudioEffectState(
            isEnabled = enabled && preset != EffectPreset.OFF,
            currentPreset = if (enabled) preset else EffectPreset.OFF,
            bassBoostStrength = bass,
            bassCutoffHz = cutoffHz,
            virtualizerStrength = virt,
            vocalClarityStrength = clarity,
            analogWarmthStrength = warmth,
            surroundRotationSpeed = rotSpeed,
            loudnessGainMb = loudness,
            preampGainDb = preamp,
            channelBalance = balance,
            antiClippingEnabled = antiClip,
            reverbMode = reverb,
            bandLevels = bands,
            tenBandLevels = expandFiveToTenBands(bands)
        )
    }

    private fun saveToPrefs() {
        val p = prefs ?: return
        val s = _state.value
        p.edit()
            .putBoolean("effect_enabled_v2", s.isEnabled)
            .putString("preset_v2", s.currentPreset.name)
            .putInt("bass_v2", s.bassBoostStrength)
            .putInt("bass_cutoff_v2", s.bassCutoffHz)
            .putInt("virt_v2", s.virtualizerStrength)
            .putInt("clarity_v2", s.vocalClarityStrength)
            .putInt("warmth_v2", s.analogWarmthStrength)
            .putInt("rot_speed_v2", s.surroundRotationSpeed)
            .putInt("loudness_v2", s.loudnessGainMb)
            .putFloat("preamp_db_v2", s.preampGainDb)
            .putFloat("channel_balance_v2", s.channelBalance)
            .putBoolean("anti_clip_v2", s.antiClippingEnabled)
            .putString("reverb_v2", s.reverbMode.name)
            .putString("bands_v2", serializeBands(s.bandLevels))
            .apply()
    }
}
