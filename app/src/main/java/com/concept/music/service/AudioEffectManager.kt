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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

enum class ReverbMode(
    val label: String,
    val preset: Short,
    val roomScale: Float,
    val decayFeedback: Float,
    val damping: Float,
    val wetMix: Float
) {
    NONE("无混响", PresetReverb.PRESET_NONE, 0f, 0f, 0f, 0f),
    SMALL_ROOM("小型房间", PresetReverb.PRESET_SMALLROOM, 0.52f, 0.44f, 0.42f, 0.18f),
    MEDIUM_ROOM("中型房间", PresetReverb.PRESET_MEDIUMROOM, 0.70f, 0.56f, 0.36f, 0.23f),
    LARGE_ROOM("大型房间", PresetReverb.PRESET_LARGEROOM, 0.86f, 0.66f, 0.32f, 0.27f),
    MEDIUM_HALL("音乐大厅", PresetReverb.PRESET_MEDIUMHALL, 1.00f, 0.74f, 0.28f, 0.31f),
    LARGE_HALL("混响礼堂", PresetReverb.PRESET_LARGEHALL, 1.18f, 0.81f, 0.24f, 0.36f),
    PLATE("录音室板式", PresetReverb.PRESET_PLATE, 0.78f, 0.76f, 0.18f, 0.29f)
}

/**
 * 发烧友级参量均衡器 (Parametric EQ) 单频段滤波器拓扑类型
 */
enum class PeqFilterType(val label: String, val shortTag: String, val desc: String) {
    PEAK("峰值对称 (Peak)", "PK", "标准钟形参量均衡，精准增减指定频点"),
    LOW_SHELF("低架搁架 (Low-Shelf)", "LS", "平滑抬升或衰减频点以下的全部低频"),
    HIGH_SHELF("高架搁架 (High-Shelf)", "HS", "平滑延伸或柔化频点以上的全部高频"),
    NOTCH("窄带陷波 (Notch)", "NT", "精准消除耳道谐振峰或驻波刺耳频点"),
    BAND_PASS("带通聚焦 (Band-Pass)", "BP", "温和聚焦频段能量并收敛两侧杂讯")
}

/**
 * 发烧友声学目标参考曲线（一键加载经典声学实验室目标频响）
 */
enum class AudiophileTargetCurve(
    val label: String,
    val subtitle: String,
    val tenBandMb: List<Int>,
    val tenBandQ: List<Float>,
    val tenBandTypes: List<PeqFilterType>
) {
    HARMAN_IE_2019(
        label = "哈曼曲线",
        subtitle = "Harman Target · 权威下潜与自然贴耳人声",
        tenBandMb = listOf(520, 440, 180, -80, -40, 140, 380, 420, 240, 180),
        tenBandQ = listOf(0.75f, 0.90f, 1.10f, 1.40f, 1.20f, 1.10f, 1.35f, 1.60f, 1.40f, 0.90f),
        tenBandTypes = listOf(
            PeqFilterType.LOW_SHELF, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK,
            PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.HIGH_SHELF
        )
    ),
    DIFFUSE_FIELD(
        label = "弥散场参考",
        subtitle = "Diffuse Field · 开阔声场与高器乐分离度",
        tenBandMb = listOf(120, 100, 60, 0, 80, 220, 360, 320, 280, 340),
        tenBandQ = listOf(0.80f, 1.00f, 1.20f, 1.20f, 1.10f, 1.15f, 1.30f, 1.50f, 1.30f, 0.85f),
        tenBandTypes = listOf(
            PeqFilterType.LOW_SHELF, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK,
            PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.HIGH_SHELF
        )
    ),
    VOCAL_SILK(
        label = "人声丝绒",
        subtitle = "中频密度强化 + 6kHz 齿音陷波柔化",
        tenBandMb = listOf(-140, -60, 80, 220, 340, 440, 380, -180, 160, 240),
        tenBandQ = listOf(0.90f, 1.10f, 1.20f, 1.00f, 0.95f, 1.05f, 1.25f, 2.80f, 1.40f, 0.90f),
        tenBandTypes = listOf(
            PeqFilterType.LOW_SHELF, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK,
            PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.HIGH_SHELF
        )
    ),
    SUB_AUDIOPHILE(
        label = "HiFi深潜低频",
        subtitle = "极低频 +6dB 弹性下潜 · 250Hz 净空防浑浊",
        tenBandMb = listOf(620, 540, 260, -160, -60, 60, 180, 240, 280, 320),
        tenBandQ = listOf(0.70f, 0.85f, 1.15f, 1.65f, 1.30f, 1.20f, 1.25f, 1.40f, 1.30f, 0.85f),
        tenBandTypes = listOf(
            PeqFilterType.LOW_SHELF, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK,
            PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.PEAK, PeqFilterType.HIGH_SHELF
        )
    ),
    STUDIO_MONITOR(
        label = "监听平直",
        subtitle = "0dB 录音室母带直通参考基准",
        tenBandMb = List(10) { 0 },
        tenBandQ = List(10) { 1.20f },
        tenBandTypes = List(10) { PeqFilterType.PEAK }
    )
}

/**
 * 用户自定义参量均衡存档槽位
 */
data class CustomPeqSlot(
    val slotIndex: Int,
    val name: String,
    val tenBandLevels: List<Int>,
    val tenBandQFactors: List<Float>,
    val tenBandCenterHz: List<Float>,
    val tenBandFilterTypes: List<PeqFilterType>,
    val preampGainDb: Float = 0f,
    val highPassHz: Int = 0,
    val lowPassHz: Int = 20000
)

/**
 * 实时音频频谱与律动遥测中心 (`AudioRhythmTelemetry`)：
 * 由 `ConceptMasteringAudioProcessor` 在音频渲染线程以零锁方式实时更新（即使音效处于 OFF 直通状态也会只读采样），
 * 供播放页「律动光晕 (RHYTHM_HALO)」背景与「参量均衡器」频谱分析仪实现跟不同歌曲同频共振的真实律动。
 */
object AudioRhythmTelemetry {
    @Volatile var bassEnergy: Float = 0f
    @Volatile var midEnergy: Float = 0f
    @Volatile var highEnergy: Float = 0f
    @Volatile var overallLevel: Float = 0f
    @Volatile var beatImpulse: Float = 0f
    @Volatile var headroomGainDb: Float = 0f
    @Volatile var limiterGainReductionDb: Float = 0f
    @Volatile var lastUpdateMs: Long = 0L

    private val bins = FloatArray(10)

    fun updateBins(newBins: FloatArray) {
        val len = minOf(bins.size, newBins.size)
        for (i in 0 until len) {
            bins[i] = newBins[i].coerceIn(0f, 1f)
        }
    }

    fun readBin(index: Int): Float {
        if (index !in 0 until 10) return 0f
        val now = System.currentTimeMillis()
        if (now - lastUpdateMs > 600L) return 0f
        return bins[index]
    }

    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        (nowMs - lastUpdateMs) in 0L..600L
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
    val bassBoostStrength: Int = 0,          // 0 ~ 1000 (超重低音下潜 Sub-Bass)
    val bassPunchStrength: Int = 0,          // 0 ~ 1000 (低频瞬态弹性冲击 Mid-Bass Punch)
    val bassCutoffHz: Int = 60,              // 40 / 55 / 60 / 75 / 95 / 110 Hz (低频分频谐振中心频点)
    val virtualizerStrength: Int = 0,        // 0 ~ 1000 (3D 空间环绕宽度)
    val soundstageDepthStrength: Int = 0,    // 0 ~ 1000 (舞台纵深与前后声像距离感)
    val crossfeedStrength: Int = 0,          // 0 ~ 1000 (Bauer 双耳交叉馈送，自然消除耳机头中效应)
    val vocalClarityStrength: Int = 0,       // 0 ~ 1000 (人声通透与泛音激励)
    val deEsserStrength: Int = 200,          // 0 ~ 1000 (6.2kHz 人声齿音动态柔化抑制)
    val trebleAirStrength: Int = 0,          // 0 ~ 1000 (12kHz+ 极高频空气泛音激励)
    val analogWarmthStrength: Int = 0,       // 0 ~ 1000 (黑胶/胆机偶次谐波温暖度)
    val transientAttackStrength: Int = 0,    // 0 ~ 1000 (瞬态微动态与鼓点包络增强)
    val surroundRotationSpeed: Int = 0,      // 0 ~ 100 (3D 旋转/动态声场速率)
    val loudnessGainMb: Int = 0,             // 0 ~ 1500 mB (0 ~ 15 dB 动态响度增益)
    val preampGainDb: Float = 0f,            // -12f ~ +12f dB (专业参量均衡器前置增益)
    val channelBalance: Float = 0f,          // -1f (左) ~ +1f (右) 声像平衡
    val antiClippingEnabled: Boolean = true, // 双声道耦合真峰值包络限幅保护 + 自动防削波预衰减
    val equalLoudnessCompensation: Boolean = false, // 夜间小音量等响度听感曲线补偿 (Fletcher-Munson)
    val volumeNormalizationEnabled: Boolean = true, // 跨曲目智能响度均衡
    val reverbMode: ReverbMode = ReverbMode.NONE,
    val reverbWetMix: Int = 260,             // 0 ~ 1000 (混响干湿比)
    val reverbPreDelayMs: Int = 18,          // 0 ~ 80 ms (混响预延迟，拉开人声与混响反射)
    val reverbDamping: Int = 350,            // 0 ~ 1000 (混响高频吸收阻尼)
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val tenBandLevels: List<Int> = List(10) { 0 },
    val tenBandQFactors: List<Float> = AudioEffectManager.DEFAULT_TEN_BAND_Q,
    val tenBandCenterHz: List<Float> = AudioEffectManager.DEFAULT_TEN_BAND_HZ,
    val tenBandFilterTypes: List<PeqFilterType> = AudioEffectManager.DEFAULT_TEN_BAND_TYPES,
    val highPassCutoffHz: Int = 0,           // 0 = Off, 20..120 Hz 高通亚音速切除滤波 (HPF)
    val lowPassCutoffHz: Int = 20000,        // 8000..20000 Hz 低通滚降滤波 (LPF, 20000 = Off)
    val selectedPeqBandIndex: Int = 0,       // 0..9 当前选中的 PEQ 精调频段
    val isAbBypassActive: Boolean = false,   // 一键 A/B 原音对比瞬时旁路开关
    val customPeqSlots: List<CustomPeqSlot> = emptyList(),
    val onlineEffectMeta: Map<String, ViperOnlineEffectMeta> = emptyMap(),
    val syncingOnlineEffects: Boolean = false
)

object AudioEffectManager {
    private var prefs: SharedPreferences? = null
    private var currentSessionId: Int = 0
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 每次音效状态或详细调音参数修改时递增，供 `ConceptMasteringAudioProcessor` 在音频帧级别零延迟感知并重算 64-bit DSP 滤波器系数
     */
    val dspConfigVersion = AtomicLong(1L)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    @Volatile
    var onAuxEffectChanged: ((effectId: Int, sendLevel: Float) -> Unit)? = null

    val TEN_BAND_FREQUENCIES = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val TEN_BAND_CENTER_HZ = doubleArrayOf(31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    val DEFAULT_TEN_BAND_HZ = TEN_BAND_CENTER_HZ.map { it.toFloat() }
    val DEFAULT_TEN_BAND_Q = List(10) { 1.20f }
    val DEFAULT_TEN_BAND_TYPES = List(10) { PeqFilterType.PEAK }
    val FIVE_BAND_FREQUENCIES = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

    val BASS_CUTOFF_OPTIONS = listOf(40, 55, 60, 75, 95, 110)

    private val _state = MutableStateFlow(AudioEffectState())
    val state = _state.asStateFlow()

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
            _state.update { it.copy(syncingOnlineEffects = true) }
            val fetched = runCatching { VipManager.music.fetchViperSoundEffects() }.getOrDefault(emptyMap())
            _state.update {
                it.copy(
                    onlineEffectMeta = if (fetched.isNotEmpty()) fetched else it.onlineEffectMeta,
                    syncingOnlineEffects = false
                )
            }
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

    /**
     * 针对 10 段参量均衡器 (31Hz ~ 16kHz, 单位 mB: -1000..+1000 即 -10dB..+10dB) 的高辨识度声学频响曲线
     */
    internal fun defaultTenBandsForPreset(preset: EffectPreset): List<Int> = when (preset) {
        EffectPreset.OFF, EffectPreset.FLAT -> List(10) { 0 }
        EffectPreset.VIPER_3D -> listOf(420, 340, 120, -140, -240, 80, 340, 520, 640, 680)
        EffectPreset.VIPER_BASS -> listOf(780, 700, 480, 180, -160, -240, -120, 80, 180, 220)
        EffectPreset.VIPER_VOCAL -> listOf(-420, -260, -60, 180, 420, 580, 640, 480, 260, 140)
        EffectPreset.VIPER_HIFI -> listOf(380, 300, 140, 60, 100, 180, 340, 500, 620, 660)
        EffectPreset.VIPER_PANORAMA_51 -> listOf(560, 460, 200, -80, -160, 120, 380, 540, 660, 700)
        EffectPreset.VIPER_BLACK_DIAMOND -> listOf(480, 400, 220, 100, 140, 240, 400, 520, 580, 520)
        EffectPreset.VIPER_CLEAR_MASTER -> listOf(280, 220, 120, 80, 160, 300, 480, 640, 760, 820)
        EffectPreset.VIPER_3D_ROTATE -> listOf(440, 320, 80, -160, -220, 140, 400, 560, 660, 640)
        EffectPreset.VIPER_SURROUND -> listOf(460, 360, 140, -120, -180, 160, 440, 600, 700, 720)
        EffectPreset.VIPER_CONCERT -> listOf(620, 520, 280, 40, -120, 140, 360, 500, 600, 580)
        EffectPreset.VIPER_LIVEHOUSE -> listOf(680, 580, 360, 140, 60, 180, 340, 460, 480, 420)
        EffectPreset.VIPER_STUDIO -> listOf(180, 140, 80, 60, 100, 180, 280, 360, 400, 380)
        EffectPreset.VIPER_VINYL -> listOf(460, 420, 320, 220, 160, 120, 40, -180, -380, -520)
        EffectPreset.VIPER_TAPE -> listOf(420, 460, 380, 260, 180, 80, -120, -280, -440, -580)
        EffectPreset.VIPER_TUBE -> listOf(400, 440, 380, 340, 360, 320, 200, 40, -160, -320)
        EffectPreset.VIPER_GRAMOPHONE -> listOf(-720, -540, -180, 320, 640, 720, 520, -120, -560, -780)
        EffectPreset.VIPER_GUFENG -> listOf(-220, -80, 120, 280, 480, 600, 660, 580, 480, 420)
        EffectPreset.VIPER_FOLK -> listOf(240, 320, 380, 320, 440, 520, 460, 360, 280, 220)
        EffectPreset.VIPER_ACG -> listOf(340, 260, 80, 120, 320, 540, 680, 740, 700, 640)
        EffectPreset.VIPER_EDM -> listOf(820, 740, 460, 80, -280, -220, 160, 480, 680, 760)
        EffectPreset.VIPER_RNB -> listOf(660, 600, 420, 180, 100, 240, 380, 440, 460, 400)
        EffectPreset.VIPER_IEM -> listOf(340, 280, 180, 100, 140, 180, -120, -320, 360, 520)
        EffectPreset.VIPER_HEADPHONE -> listOf(440, 360, 220, 100, 60, 160, 300, 420, 520, 540)
        EffectPreset.VIPER_TWS -> listOf(580, 480, 240, 60, 80, 220, 400, 540, 660, 700)
        EffectPreset.VIPER_CAR_LUXURY -> listOf(640, 540, 320, 120, 100, 240, 400, 520, 600, 620)
        EffectPreset.VIPER_CAR_SUBWOOFER -> listOf(860, 780, 540, 220, -140, -100, 140, 280, 360, 380)
        EffectPreset.VIPER_DYNAMIC -> listOf(580, 480, 260, 60, -40, 120, 320, 480, 600, 640)
        EffectPreset.ROCK -> listOf(560, 480, 280, -120, -360, -200, 220, 460, 580, 620)
        EffectPreset.POP -> listOf(320, 280, 340, 440, 520, 480, 380, 280, 220, 180)
        EffectPreset.EQ_CLASSICAL -> listOf(340, 280, 140, 0, -60, 80, 260, 420, 540, 600)
        EffectPreset.EQ_JAZZ -> listOf(400, 360, 260, 180, 140, 220, 320, 380, 420, 380)
        EffectPreset.EQ_DANCE -> listOf(720, 640, 400, 80, -220, -140, 180, 420, 580, 640)
        EffectPreset.EQ_HIPHOP -> listOf(780, 680, 440, 160, 0, 140, 320, 400, 460, 480)
        EffectPreset.EQ_ACOUSTIC -> listOf(260, 300, 340, 280, 360, 420, 440, 400, 360, 320)
        EffectPreset.EQ_SOFT -> listOf(280, 260, 200, 120, 40, -80, -220, -360, -480, -560)
        EffectPreset.CUSTOM -> expandFiveToTenBands(defaultBandsForPreset(EffectPreset.CUSTOM))
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

    /**
     * 计算当前状态在 10 个中心频点上的综合等效频响曲线（单位 mB，-1000..+1000 对应 -10dB..+10dB）：
     * 同时融合 10 段参量均衡推杆、重低音下潜、低频瞬态冲击、截止频点、人声通透、齿音柔化、极高频空气泛音、胆机温暖度与等响度补偿。
     */
    fun computeEffectiveTenBandsMb(state: AudioEffectState): List<Int> {
        val baseTen = if (state.tenBandLevels.size == 10) state.tenBandLevels else List(10) { 0 }
        if (!state.isEnabled || state.isAbBypassActive) return baseTen

        val bassNorm = (state.bassBoostStrength / 1000f).coerceIn(0f, 1f)
        val punchNorm = (state.bassPunchStrength / 1000f).coerceIn(0f, 1f)
        val clarityNorm = (state.vocalClarityStrength / 1000f).coerceIn(0f, 1f)
        val airNorm = (state.trebleAirStrength / 1000f).coerceIn(0f, 1f)
        val deEssNorm = (state.deEsserStrength / 1000f).coerceIn(0f, 1f)
        val warmthNorm = (state.analogWarmthStrength / 1000f).coerceIn(0f, 1f)
        val eqLoudness = state.equalLoudnessCompensation
        val cutoff = state.bassCutoffHz

        return List(10) { idx ->
            val baseMb = baseTen[idx]
            val bassModMb = when (idx) {
                0 -> (bassNorm * (if (cutoff <= 50) 560f else if (cutoff <= 65) 480f else 380f)).toInt()
                1 -> (bassNorm * (if (cutoff <= 50) 420f else if (cutoff <= 65) 500f else 460f) + punchNorm * 280f).toInt()
                2 -> (bassNorm * (if (cutoff <= 50) 160f else if (cutoff <= 65) 220f else 340f) + punchNorm * 220f).toInt()
                3 -> (-bassNorm * 80f - punchNorm * 60f).toInt() // 主动净空 250Hz 防止低频掩蔽人声
                else -> 0
            }
            val clarityModMb = when (idx) {
                3 -> (-clarityNorm * 120f).toInt()
                5 -> (clarityNorm * 260f).toInt()
                6 -> (clarityNorm * 420f).toInt()
                7 -> (clarityNorm * 440f - deEssNorm * 140f).toInt()
                8 -> (clarityNorm * 280f - deEssNorm * 220f + airNorm * 320f).toInt()
                9 -> (clarityNorm * 240f + airNorm * 520f).toInt()
                else -> 0
            }
            val warmthModMb = when (idx) {
                1 -> (warmthNorm * 180f).toInt()
                2 -> (warmthNorm * 320f).toInt()
                3 -> (warmthNorm * 280f).toInt()
                4 -> (warmthNorm * 160f).toInt()
                8 -> (-warmthNorm * 150f).toInt()
                9 -> (-warmthNorm * 220f).toInt()
                else -> 0
            }
            val loudnessCompMb = if (eqLoudness) {
                when (idx) {
                    0 -> 360
                    1 -> 260
                    2 -> 100
                    8 -> 200
                    9 -> 300
                    else -> 0
                }
            } else 0

            (baseMb + bassModMb + clarityModMb + warmthModMb + loudnessCompMb).coerceIn(-1000, 1000)
        }
    }

    /**
     * 精确计算级联二阶 Biquad 参量滤波器组在任意频率 `freqHz` (20Hz ~ 20kHz) 处的真实幅频响应 (dB)：
     * 完整体现每个频段的中心频率 `fc`、品质因数 `Q`、滤波器拓扑类型 (`PEAK` / `LOW_SHELF` / `HIGH_SHELF` / `NOTCH` / `BAND_PASS`)、
     * 高通/低通截止频点以及声场调音补偿，供发烧级 PEQ 频响曲线画布实时绘制。
     */
    fun evaluatePeqMagnitudeDbAtFrequency(
        freqHz: Double,
        state: AudioEffectState,
        sampleRate: Double = 48000.0
    ): Float {
        if (state.isAbBypassActive) return 0f
        val f = freqHz.coerceIn(18.0, sampleRate * 0.48)
        val w = 2.0 * PI * f / sampleRate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        fun biquadMagDb(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double): Double {
            val numRe = b0 + b1 * cosW + b2 * cos2W
            val numIm = -(b1 * sinW + b2 * sin2W)
            val denRe = 1.0 + a1 * cosW + a2 * cos2W
            val denIm = -(a1 * sinW + a2 * sin2W)
            val numSq = numRe * numRe + numIm * numIm
            val denSq = (denRe * denRe + denIm * denIm).coerceAtLeast(1e-18)
            return 10.0 * log10((numSq / denSq).coerceAtLeast(1e-12))
        }

        var totalDb = 0.0
        val gainsMb = state.tenBandLevels
        val qs = state.tenBandQFactors
        val centers = state.tenBandCenterHz
        val types = state.tenBandFilterTypes

        for (i in 0 until 10) {
            val gainDb = (gainsMb.getOrElse(i) { 0 }) / 100.0
            val fc = (centers.getOrElse(i) { DEFAULT_TEN_BAND_HZ[i] }).toDouble().coerceIn(20.0, sampleRate * 0.46)
            val q = (qs.getOrElse(i) { 1.20f }).toDouble().coerceIn(0.25, 10.0)
            val type = types.getOrElse(i) { PeqFilterType.PEAK }

            if (type == PeqFilterType.PEAK || type == PeqFilterType.LOW_SHELF || type == PeqFilterType.HIGH_SHELF) {
                if (abs(gainDb) < 0.03) continue
            }

            val w0 = 2.0 * PI * fc / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)

            var b0 = 1.0
            var b1 = 0.0
            var b2 = 0.0
            var a0 = 1.0
            var a1 = 0.0
            var a2 = 0.0

            when (type) {
                PeqFilterType.PEAK -> {
                    val A = 10.0.pow(gainDb / 40.0)
                    b0 = 1.0 + alpha * A
                    b1 = -2.0 * cosW0
                    b2 = 1.0 - alpha * A
                    a0 = 1.0 + alpha / A
                    a1 = -2.0 * cosW0
                    a2 = 1.0 - alpha / A
                }
                PeqFilterType.LOW_SHELF -> {
                    val A = 10.0.pow(gainDb / 40.0)
                    val sSlope = (q / 1.2).coerceIn(0.35, 2.5)
                    val alphaS = sinW0 / 2.0 * sqrt((A + 1.0 / A) * (1.0 / sSlope - 1.0) + 2.0)
                    val twoSqrtAAlpha = 2.0 * sqrt(A) * alphaS
                    b0 = A * ((A + 1.0) - (A - 1.0) * cosW0 + twoSqrtAAlpha)
                    b1 = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW0)
                    b2 = A * ((A + 1.0) - (A - 1.0) * cosW0 - twoSqrtAAlpha)
                    a0 = (A + 1.0) + (A - 1.0) * cosW0 + twoSqrtAAlpha
                    a1 = -2.0 * ((A - 1.0) + (A + 1.0) * cosW0)
                    a2 = (A + 1.0) + (A - 1.0) * cosW0 - twoSqrtAAlpha
                }
                PeqFilterType.HIGH_SHELF -> {
                    val A = 10.0.pow(gainDb / 40.0)
                    val sSlope = (q / 1.2).coerceIn(0.35, 2.5)
                    val alphaS = sinW0 / 2.0 * sqrt((A + 1.0 / A) * (1.0 / sSlope - 1.0) + 2.0)
                    val twoSqrtAAlpha = 2.0 * sqrt(A) * alphaS
                    b0 = A * ((A + 1.0) + (A - 1.0) * cosW0 + twoSqrtAAlpha)
                    b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW0)
                    b2 = A * ((A + 1.0) + (A - 1.0) * cosW0 - twoSqrtAAlpha)
                    a0 = (A + 1.0) - (A - 1.0) * cosW0 + twoSqrtAAlpha
                    a1 = 2.0 * ((A - 1.0) - (A + 1.0) * cosW0)
                    a2 = (A + 1.0) - (A - 1.0) * cosW0 - twoSqrtAAlpha
                }
                PeqFilterType.NOTCH -> {
                    // 陷波深度可随负向增益调节，默认至少 -12dB 窄带陷波
                    val cutDb = if (gainDb < -0.5) gainDb else -10.0
                    val A = 10.0.pow(cutDb / 40.0)
                    val notchAlpha = sinW0 / (2.0 * (q * 2.2).coerceAtLeast(1.8))
                    b0 = 1.0 + notchAlpha * A
                    b1 = -2.0 * cosW0
                    b2 = 1.0 - notchAlpha * A
                    a0 = 1.0 + notchAlpha / A
                    a1 = -2.0 * cosW0
                    a2 = 1.0 - notchAlpha / A
                }
                PeqFilterType.BAND_PASS -> {
                    val A = 10.0.pow((gainDb.coerceIn(-8.0, 8.0)) / 40.0)
                    b0 = 1.0 + alpha * A
                    b1 = -2.0 * cosW0
                    b2 = 1.0 - alpha * A
                    a0 = 1.0 + alpha / A
                    a1 = -2.0 * cosW0
                    a2 = 1.0 - alpha / A
                }
            }
            totalDb += biquadMagDb(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        // 高通滤波 (HPF) 频响叠加
        if (state.highPassCutoffHz in 20..120) {
            val w0 = 2.0 * PI * state.highPassCutoffHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * 0.7071)
            val b0 = (1.0 + cosW0) / 2.0
            val b1 = -(1.0 + cosW0)
            val b2 = (1.0 + cosW0) / 2.0
            val a0 = 1.0 + alpha
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha
            totalDb += biquadMagDb(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        // 低通滤波 (LPF) 频响叠加
        if (state.lowPassCutoffHz in 6000..19500) {
            val w0 = 2.0 * PI * state.lowPassCutoffHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * 0.7071)
            val b0 = (1.0 - cosW0) / 2.0
            val b1 = 1.0 - cosW0
            val b2 = (1.0 - cosW0) / 2.0
            val a0 = 1.0 + alpha
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha
            totalDb += biquadMagDb(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        return totalDb.toFloat().coerceIn(-15f, 15f)
    }

    private data class PresetDetailedProfile(
        val bass: Int,
        val punch: Int,
        val cutoffHz: Int,
        val virt: Int,
        val depth: Int,
        val crossfeed: Int,
        val clarity: Int,
        val deEsser: Int,
        val air: Int,
        val warmth: Int,
        val transientAttack: Int,
        val rotationSpeed: Int,
        val loudness: Int,
        val reverb: ReverbMode,
        val reverbWet: Int,
        val reverbPreDelay: Int,
        val reverbDamp: Int,
        val bands: List<Int>,
        val tenBands: List<Int>
    )

    private fun profileForPreset(preset: EffectPreset): PresetDetailedProfile {
        val bands = defaultBandsForPreset(preset)
        val tenBands = defaultTenBandsForPreset(preset)
        return when (preset) {
            EffectPreset.OFF -> PresetDetailedProfile(0, 0, 60, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ReverbMode.NONE, 0, 15, 300, bands, tenBands)
            EffectPreset.FLAT -> PresetDetailedProfile(0, 0, 60, 0, 0, 0, 0, 150, 0, 0, 0, 0, 0, ReverbMode.NONE, 0, 15, 300, bands, tenBands)
            EffectPreset.VIPER_3D -> PresetDetailedProfile(360, 260, 60, 560, 420, 180, 460, 220, 340, 160, 240, 0, 260, ReverbMode.SMALL_ROOM, 220, 18, 340, bands, tenBands)
            EffectPreset.VIPER_VOCAL -> PresetDetailedProfile(60, 120, 85, 220, 180, 240, 680, 380, 260, 280, 160, 0, 220, ReverbMode.SMALL_ROOM, 180, 22, 420, bands, tenBands)
            EffectPreset.VIPER_BASS -> PresetDetailedProfile(720, 640, 55, 240, 220, 140, 220, 240, 180, 340, 480, 0, 340, ReverbMode.NONE, 0, 15, 350, bands, tenBands)
            EffectPreset.VIPER_HIFI -> PresetDetailedProfile(280, 320, 60, 460, 380, 220, 440, 240, 380, 240, 360, 0, 260, ReverbMode.MEDIUM_HALL, 260, 24, 300, bands, tenBands)
            EffectPreset.VIPER_PANORAMA_51 -> PresetDetailedProfile(420, 340, 55, 720, 640, 280, 420, 220, 360, 200, 280, 24, 320, ReverbMode.LARGE_HALL, 340, 28, 280, bands, tenBands)
            EffectPreset.VIPER_BLACK_DIAMOND -> PresetDetailedProfile(200, 280, 60, 260, 240, 200, 420, 260, 420, 380, 320, 0, 280, ReverbMode.SMALL_ROOM, 180, 18, 380, bands, tenBands)
            EffectPreset.VIPER_CLEAR_MASTER -> PresetDetailedProfile(220, 260, 60, 340, 260, 180, 680, 320, 680, 160, 420, 0, 260, ReverbMode.NONE, 0, 15, 300, bands, tenBands)
            EffectPreset.VIPER_3D_ROTATE -> PresetDetailedProfile(300, 240, 60, 780, 560, 120, 360, 220, 300, 140, 220, 72, 280, ReverbMode.MEDIUM_HALL, 280, 22, 320, bands, tenBands)
            EffectPreset.VIPER_SURROUND -> PresetDetailedProfile(360, 280, 60, 680, 580, 360, 380, 220, 320, 180, 240, 18, 300, ReverbMode.LARGE_HALL, 320, 26, 300, bands, tenBands)
            EffectPreset.VIPER_CONCERT -> PresetDetailedProfile(440, 360, 60, 680, 720, 220, 380, 240, 300, 240, 300, 0, 340, ReverbMode.LARGE_HALL, 380, 36, 260, bands, tenBands)
            EffectPreset.VIPER_LIVEHOUSE -> PresetDetailedProfile(480, 520, 60, 460, 400, 180, 420, 240, 260, 320, 460, 0, 320, ReverbMode.MEDIUM_ROOM, 250, 16, 360, bands, tenBands)
            EffectPreset.VIPER_STUDIO -> PresetDetailedProfile(140, 200, 60, 180, 220, 320, 480, 280, 320, 140, 300, 0, 160, ReverbMode.SMALL_ROOM, 140, 12, 460, bands, tenBands)
            EffectPreset.VIPER_VINYL -> PresetDetailedProfile(300, 220, 60, 240, 260, 260, 200, 360, 80, 680, 120, 0, 220, ReverbMode.SMALL_ROOM, 160, 18, 520, bands, tenBands)
            EffectPreset.VIPER_TAPE -> PresetDetailedProfile(340, 260, 75, 200, 220, 240, 180, 380, 60, 720, 140, 0, 240, ReverbMode.SMALL_ROOM, 150, 16, 560, bands, tenBands)
            EffectPreset.VIPER_TUBE -> PresetDetailedProfile(280, 240, 60, 260, 280, 240, 340, 340, 140, 780, 180, 0, 260, ReverbMode.SMALL_ROOM, 180, 20, 480, bands, tenBands)
            EffectPreset.VIPER_GRAMOPHONE -> PresetDetailedProfile(0, 80, 95, 100, 160, 300, 360, 420, 0, 640, 100, 0, 200, ReverbMode.SMALL_ROOM, 160, 14, 620, bands, tenBands)
            EffectPreset.VIPER_GUFENG -> PresetDetailedProfile(120, 160, 75, 440, 460, 220, 620, 300, 480, 240, 280, 0, 240, ReverbMode.MEDIUM_HALL, 280, 28, 300, bands, tenBands)
            EffectPreset.VIPER_FOLK -> PresetDetailedProfile(220, 280, 60, 300, 320, 280, 500, 280, 300, 440, 360, 0, 220, ReverbMode.SMALL_ROOM, 200, 20, 380, bands, tenBands)
            EffectPreset.VIPER_ACG -> PresetDetailedProfile(260, 440, 60, 380, 280, 180, 660, 360, 560, 140, 520, 0, 280, ReverbMode.NONE, 0, 12, 300, bands, tenBands)
            EffectPreset.VIPER_EDM -> PresetDetailedProfile(760, 720, 40, 520, 380, 140, 480, 260, 540, 200, 640, 0, 380, ReverbMode.LARGE_ROOM, 240, 20, 280, bands, tenBands)
            EffectPreset.VIPER_RNB -> PresetDetailedProfile(540, 480, 55, 380, 340, 220, 420, 300, 280, 440, 380, 0, 280, ReverbMode.SMALL_ROOM, 200, 18, 380, bands, tenBands)
            EffectPreset.VIPER_IEM -> PresetDetailedProfile(260, 240, 60, 460, 380, 480, 360, 520, 320, 280, 240, 0, 200, ReverbMode.NONE, 0, 15, 360, bands, tenBands)
            EffectPreset.VIPER_HEADPHONE -> PresetDetailedProfile(300, 280, 55, 520, 480, 560, 420, 260, 360, 300, 280, 0, 240, ReverbMode.SMALL_ROOM, 180, 18, 360, bands, tenBands)
            EffectPreset.VIPER_TWS -> PresetDetailedProfile(460, 420, 60, 420, 320, 320, 500, 280, 520, 220, 380, 0, 280, ReverbMode.NONE, 0, 15, 320, bands, tenBands)
            EffectPreset.VIPER_CAR_LUXURY -> PresetDetailedProfile(480, 440, 55, 600, 520, 200, 460, 260, 380, 340, 360, 0, 340, ReverbMode.MEDIUM_ROOM, 260, 22, 340, bands, tenBands)
            EffectPreset.VIPER_CAR_SUBWOOFER -> PresetDetailedProfile(800, 680, 40, 380, 340, 160, 300, 240, 220, 360, 520, 0, 380, ReverbMode.SMALL_ROOM, 180, 16, 400, bands, tenBands)
            EffectPreset.VIPER_DYNAMIC -> PresetDetailedProfile(440, 460, 60, 380, 300, 200, 460, 240, 400, 220, 540, 0, 320, ReverbMode.NONE, 0, 15, 320, bands, tenBands)
            EffectPreset.ROCK -> PresetDetailedProfile(400, 480, 60, 300, 280, 180, 400, 260, 340, 220, 500, 0, 280, ReverbMode.SMALL_ROOM, 180, 16, 360, bands, tenBands)
            EffectPreset.POP -> PresetDetailedProfile(260, 280, 60, 240, 220, 200, 440, 280, 280, 260, 280, 0, 220, ReverbMode.NONE, 0, 15, 340, bands, tenBands)
            EffectPreset.EQ_CLASSICAL -> PresetDetailedProfile(200, 180, 55, 440, 540, 280, 460, 200, 420, 220, 320, 0, 200, ReverbMode.MEDIUM_HALL, 300, 30, 280, bands, tenBands)
            EffectPreset.EQ_JAZZ -> PresetDetailedProfile(280, 260, 60, 320, 360, 280, 380, 260, 240, 480, 260, 0, 220, ReverbMode.SMALL_ROOM, 220, 20, 400, bands, tenBands)
            EffectPreset.EQ_DANCE -> PresetDetailedProfile(600, 580, 55, 360, 280, 160, 380, 240, 380, 180, 520, 0, 320, ReverbMode.NONE, 0, 15, 320, bands, tenBands)
            EffectPreset.EQ_HIPHOP -> PresetDetailedProfile(660, 560, 40, 300, 260, 180, 420, 280, 300, 280, 460, 0, 320, ReverbMode.NONE, 0, 15, 340, bands, tenBands)
            EffectPreset.EQ_ACOUSTIC -> PresetDetailedProfile(200, 220, 60, 260, 300, 300, 460, 260, 340, 360, 300, 0, 180, ReverbMode.SMALL_ROOM, 180, 18, 380, bands, tenBands)
            EffectPreset.EQ_SOFT -> PresetDetailedProfile(180, 100, 60, 200, 240, 380, 160, 440, 0, 480, 80, 0, 120, ReverbMode.NONE, 0, 15, 480, bands, tenBands)
            EffectPreset.CUSTOM -> {
                val cur = _state.value
                PresetDetailedProfile(
                    cur.bassBoostStrength,
                    cur.bassPunchStrength,
                    cur.bassCutoffHz,
                    cur.virtualizerStrength,
                    cur.soundstageDepthStrength,
                    cur.crossfeedStrength,
                    cur.vocalClarityStrength,
                    cur.deEsserStrength,
                    cur.trebleAirStrength,
                    cur.analogWarmthStrength,
                    cur.transientAttackStrength,
                    cur.surroundRotationSpeed,
                    cur.loudnessGainMb,
                    cur.reverbMode,
                    cur.reverbWetMix,
                    cur.reverbPreDelayMs,
                    cur.reverbDamping,
                    cur.bandLevels,
                    cur.tenBandLevels.let { if (it.size == 10) it else expandFiveToTenBands(cur.bandLevels) }
                )
            }
        }
    }

    private fun resolveActivePresetWhenTuning(current: EffectPreset): EffectPreset =
        if (current == EffectPreset.OFF) EffectPreset.CUSTOM else current

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
        _state.value = _state.value.copy(isEnabled = enabled, currentPreset = nextPreset, isAbBypassActive = false)
        applyCurrentState()
        saveToPrefs()
    }

    fun setPreset(preset: EffectPreset) {
        val p = profileForPreset(preset)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = preset != EffectPreset.OFF,
            currentPreset = preset,
            isAbBypassActive = false,
            bassBoostStrength = p.bass,
            bassPunchStrength = p.punch,
            bassCutoffHz = p.cutoffHz,
            virtualizerStrength = p.virt,
            soundstageDepthStrength = p.depth,
            crossfeedStrength = p.crossfeed,
            vocalClarityStrength = p.clarity,
            deEsserStrength = p.deEsser,
            trebleAirStrength = p.air,
            analogWarmthStrength = p.warmth,
            transientAttackStrength = p.transientAttack,
            surroundRotationSpeed = p.rotationSpeed,
            loudnessGainMb = p.loudness,
            reverbMode = p.reverb,
            reverbWetMix = p.reverbWet,
            reverbPreDelayMs = p.reverbPreDelay,
            reverbDamping = p.reverbDamp,
            bandLevels = p.bands,
            tenBandLevels = p.tenBands,
            tenBandQFactors = if (preset == EffectPreset.CUSTOM) cur.tenBandQFactors else DEFAULT_TEN_BAND_Q,
            tenBandCenterHz = if (preset == EffectPreset.CUSTOM) cur.tenBandCenterHz else DEFAULT_TEN_BAND_HZ,
            tenBandFilterTypes = if (preset == EffectPreset.CUSTOM) cur.tenBandFilterTypes else DEFAULT_TEN_BAND_TYPES
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun resetCurrentPresetToDefault() {
        val current = _state.value.currentPreset
        _state.value = _state.value.copy(
            tenBandQFactors = DEFAULT_TEN_BAND_Q,
            tenBandCenterHz = DEFAULT_TEN_BAND_HZ,
            tenBandFilterTypes = DEFAULT_TEN_BAND_TYPES,
            highPassCutoffHz = 0,
            lowPassCutoffHz = 20000,
            preampGainDb = 0f,
            isAbBypassActive = false
        )
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
            if (it.size == 10) it else defaultTenBandsForPreset(_state.value.currentPreset).toMutableList()
        }
        if (tenBandIndex in currentTen.indices) {
            currentTen[tenBandIndex] = levelMb.coerceIn(-1000, 1000)
            val mappedFive = compressTenToFiveBands(currentTen)
            _state.value = _state.value.copy(
                isEnabled = true,
                currentPreset = EffectPreset.CUSTOM,
                selectedPeqBandIndex = tenBandIndex,
                bandLevels = mappedFive,
                tenBandLevels = currentTen
            )
            applyCurrentState()
            saveToPrefs()
        }
    }

    fun setSelectedPeqBandIndex(index: Int) {
        val idx = index.coerceIn(0, 9)
        _state.value = _state.value.copy(selectedPeqBandIndex = idx)
    }

    fun setTenBandQFactor(bandIndex: Int, qFactor: Float) {
        val cur = _state.value
        val qs = cur.tenBandQFactors.toMutableList().let {
            if (it.size == 10) it else DEFAULT_TEN_BAND_Q.toMutableList()
        }
        if (bandIndex in qs.indices) {
            qs[bandIndex] = ((qFactor.coerceIn(0.30f, 8.00f) * 20f).toInt() / 20f)
            _state.value = cur.copy(
                isEnabled = true,
                currentPreset = EffectPreset.CUSTOM,
                selectedPeqBandIndex = bandIndex,
                tenBandQFactors = qs
            )
            applyCurrentState()
            saveToPrefs()
        }
    }

    fun setTenBandCenterFrequency(bandIndex: Int, freqHz: Float) {
        val cur = _state.value
        val freqs = cur.tenBandCenterHz.toMutableList().let {
            if (it.size == 10) it else DEFAULT_TEN_BAND_HZ.toMutableList()
        }
        if (bandIndex in freqs.indices) {
            val defaultFc = DEFAULT_TEN_BAND_HZ[bandIndex]
            val minFc = (defaultFc * 0.55f).coerceAtLeast(20f)
            val maxFc = (defaultFc * 1.80f).coerceAtMost(20000f)
            freqs[bandIndex] = freqHz.coerceIn(minFc, maxFc)
            _state.value = cur.copy(
                isEnabled = true,
                currentPreset = EffectPreset.CUSTOM,
                selectedPeqBandIndex = bandIndex,
                tenBandCenterHz = freqs
            )
            applyCurrentState()
            saveToPrefs()
        }
    }

    fun setTenBandFilterType(bandIndex: Int, filterType: PeqFilterType) {
        val cur = _state.value
        val types = cur.tenBandFilterTypes.toMutableList().let {
            if (it.size == 10) it else DEFAULT_TEN_BAND_TYPES.toMutableList()
        }
        if (bandIndex in types.indices) {
            types[bandIndex] = filterType
            _state.value = cur.copy(
                isEnabled = true,
                currentPreset = EffectPreset.CUSTOM,
                selectedPeqBandIndex = bandIndex,
                tenBandFilterTypes = types
            )
            applyCurrentState()
            saveToPrefs()
        }
    }

    fun setHighPassCutoffHz(cutoffHz: Int) {
        val hz = if (cutoffHz < 20) 0 else cutoffHz.coerceIn(20, 120)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            highPassCutoffHz = hz
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setLowPassCutoffHz(cutoffHz: Int) {
        val hz = cutoffHz.coerceIn(8000, 20000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            lowPassCutoffHz = hz
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setAbBypassActive(bypass: Boolean) {
        _state.value = _state.value.copy(isAbBypassActive = bypass)
        dspConfigVersion.incrementAndGet()
    }

    fun applyAudiophileTargetCurve(target: AudiophileTargetCurve) {
        val cur = _state.value
        val mappedFive = compressTenToFiveBands(target.tenBandMb)
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = EffectPreset.CUSTOM,
            isAbBypassActive = false,
            bandLevels = mappedFive,
            tenBandLevels = target.tenBandMb,
            tenBandQFactors = target.tenBandQ,
            tenBandCenterHz = DEFAULT_TEN_BAND_HZ,
            tenBandFilterTypes = target.tenBandTypes
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun saveCustomPeqSlot(slotIndex: Int, customName: String = "自定义调音 ${('A' + slotIndex.coerceIn(0, 2))}") {
        val idx = slotIndex.coerceIn(0, 2)
        val cur = _state.value
        val newSlot = CustomPeqSlot(
            slotIndex = idx,
            name = customName.ifBlank { "自定义调音 ${('A' + idx)}" },
            tenBandLevels = cur.tenBandLevels,
            tenBandQFactors = cur.tenBandQFactors,
            tenBandCenterHz = cur.tenBandCenterHz,
            tenBandFilterTypes = cur.tenBandFilterTypes,
            preampGainDb = cur.preampGainDb,
            highPassHz = cur.highPassCutoffHz,
            lowPassHz = cur.lowPassCutoffHz
        )
        val updated = cur.customPeqSlots.filterNot { it.slotIndex == idx }.plus(newSlot).sortedBy { it.slotIndex }
        _state.value = cur.copy(customPeqSlots = updated)
        saveToPrefs()
    }

    fun loadCustomPeqSlot(slotIndex: Int): Boolean {
        val cur = _state.value
        val slot = cur.customPeqSlots.firstOrNull { it.slotIndex == slotIndex } ?: return false
        val mappedFive = compressTenToFiveBands(slot.tenBandLevels)
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = EffectPreset.CUSTOM,
            isAbBypassActive = false,
            bandLevels = mappedFive,
            tenBandLevels = slot.tenBandLevels,
            tenBandQFactors = slot.tenBandQFactors,
            tenBandCenterHz = slot.tenBandCenterHz,
            tenBandFilterTypes = slot.tenBandFilterTypes,
            preampGainDb = slot.preampGainDb,
            highPassCutoffHz = slot.highPassHz,
            lowPassCutoffHz = slot.lowPassHz
        )
        applyCurrentState()
        saveToPrefs()
        return true
    }

    fun setBassBoostStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            bassBoostStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setBassPunchStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            bassPunchStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setBassCutoffHz(cutoffHz: Int) {
        val c = cutoffHz.coerceIn(40, 120)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            bassCutoffHz = c
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setVirtualizerStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            virtualizerStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setSoundstageDepthStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            soundstageDepthStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setCrossfeedStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            crossfeedStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setVocalClarityStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            vocalClarityStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setDeEsserStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            deEsserStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setTrebleAirStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            trebleAirStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setAnalogWarmthStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            analogWarmthStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setTransientAttackStrength(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            transientAttackStrength = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setSurroundRotationSpeed(speed: Int) {
        val s = speed.coerceIn(0, 100)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            surroundRotationSpeed = s
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setLoudnessGain(gainMb: Int) {
        val g = gainMb.coerceIn(0, 1500)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            loudnessGainMb = g
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setPreampGainDb(gainDb: Float) {
        val g = gainDb.coerceIn(-12f, 12f)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
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
        dspConfigVersion.incrementAndGet()
        saveToPrefs()
    }

    fun setAntiClippingEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(antiClippingEnabled = enabled)
        applyCurrentState()
        saveToPrefs()
    }

    fun setEqualLoudnessCompensation(enabled: Boolean) {
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = if (enabled) true else cur.isEnabled,
            currentPreset = if (enabled) resolveActivePresetWhenTuning(cur.currentPreset) else cur.currentPreset,
            equalLoudnessCompensation = enabled
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(volumeNormalizationEnabled = enabled)
        dspConfigVersion.incrementAndGet()
        saveToPrefs()
    }

    fun setReverbMode(mode: ReverbMode) {
        val cur = _state.value
        val defaultWet = if (mode == ReverbMode.NONE) 0 else (mode.wetMix * 1000f).toInt().coerceIn(120, 480)
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            reverbMode = mode,
            reverbWetMix = if (cur.reverbWetMix == 0 && mode != ReverbMode.NONE) defaultWet else cur.reverbWetMix
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setReverbWetMix(wetPermille: Int) {
        val w = wetPermille.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            reverbWetMix = w
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setReverbPreDelayMs(preDelayMs: Int) {
        val pd = preDelayMs.coerceIn(0, 80)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            reverbPreDelayMs = pd
        )
        applyCurrentState()
        saveToPrefs()
    }

    fun setReverbDamping(dampingPermille: Int) {
        val d = dampingPermille.coerceIn(0, 1000)
        val cur = _state.value
        _state.value = cur.copy(
            isEnabled = true,
            currentPreset = resolveActivePresetWhenTuning(cur.currentPreset),
            reverbDamping = d
        )
        applyCurrentState()
        saveToPrefs()
    }

    private fun applyCurrentState() {
        dspConfigVersion.incrementAndGet()
        val s = _state.value
        if (!s.isEnabled || s.currentPreset == EffectPreset.OFF) {
            releaseEffects()
            return
        }
        releaseEffects()
    }

    internal fun releaseEffects() {
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
        onAuxEffectChanged?.invoke(0, 0f)
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        val enabled = p.getBoolean("effect_enabled_v2", false)
        val presetName = p.getString("preset_v2", EffectPreset.OFF.name) ?: EffectPreset.OFF.name
        val preset = runCatching { EffectPreset.valueOf(presetName) }.getOrDefault(EffectPreset.OFF)
        val activePreset = if (enabled && preset != EffectPreset.OFF) preset else EffectPreset.OFF
        val defaultProf = profileForPreset(activePreset)

        val bass = p.getInt("bass_v2", defaultProf.bass)
        val punch = p.getInt("bass_punch_v3", defaultProf.punch)
        val cutoffHz = p.getInt("bass_cutoff_v2", defaultProf.cutoffHz)
        val virt = p.getInt("virt_v2", defaultProf.virt)
        val depth = p.getInt("depth_v3", defaultProf.depth)
        val crossfeed = p.getInt("crossfeed_v3", defaultProf.crossfeed)
        val clarity = p.getInt("clarity_v2", defaultProf.clarity)
        val deEsser = p.getInt("de_esser_v3", defaultProf.deEsser)
        val air = p.getInt("treble_air_v3", defaultProf.air)
        val warmth = p.getInt("warmth_v2", defaultProf.warmth)
        val transientAttack = p.getInt("transient_attack_v3", defaultProf.transientAttack)
        val rotSpeed = p.getInt("rot_speed_v2", defaultProf.rotationSpeed)
        val loudness = p.getInt("loudness_v2", defaultProf.loudness)
        val preamp = p.getFloat("preamp_db_v2", 0f)
        val balance = p.getFloat("channel_balance_v2", 0f)
        val antiClip = p.getBoolean("anti_clip_v2", true)
        val eqLoudness = p.getBoolean("eq_loudness_comp_v2", false)
        val volNorm = p.getBoolean("vol_norm_v2", true)
        val reverbName = p.getString("reverb_v2", defaultProf.reverb.name) ?: defaultProf.reverb.name
        val reverb = runCatching { ReverbMode.valueOf(reverbName) }.getOrDefault(ReverbMode.NONE)
        val reverbWet = p.getInt("reverb_wet_v3", defaultProf.reverbWet)
        val reverbPreDelay = p.getInt("reverb_predelay_v3", defaultProf.reverbPreDelay)
        val reverbDamp = p.getInt("reverb_damp_v3", defaultProf.reverbDamp)
        val hpfHz = p.getInt("peq_hpf_v3", 0)
        val lpfHz = p.getInt("peq_lpf_v3", 20000)

        val bandsRaw = p.getString("bands_v2", null)
        val bands = parseBandsString(bandsRaw, activePreset)
        val tenBandsRaw = p.getString("ten_bands_v3", null)
        val restoredTenBands = if (!tenBandsRaw.isNullOrBlank()) {
            val parsedTen = tenBandsRaw.split(",").mapNotNull { it.trim().toIntOrNull()?.coerceIn(-1000, 1000) }
            if (parsedTen.size == 10) parsedTen else defaultProf.tenBands
        } else {
            defaultProf.tenBands
        }

        val qsRaw = p.getString("ten_band_qs_v3", null)
        val restoredQs = if (!qsRaw.isNullOrBlank()) {
            val parsed = qsRaw.split(",").mapNotNull { it.trim().toFloatOrNull()?.coerceIn(0.30f, 8.00f) }
            if (parsed.size == 10) parsed else DEFAULT_TEN_BAND_Q
        } else DEFAULT_TEN_BAND_Q

        val hzRaw = p.getString("ten_band_hz_v3", null)
        val restoredHz = if (!hzRaw.isNullOrBlank()) {
            val parsed = hzRaw.split(",").mapNotNull { it.trim().toFloatOrNull()?.coerceIn(20f, 20000f) }
            if (parsed.size == 10) parsed else DEFAULT_TEN_BAND_HZ
        } else DEFAULT_TEN_BAND_HZ

        val typesRaw = p.getString("ten_band_types_v3", null)
        val restoredTypes = if (!typesRaw.isNullOrBlank()) {
            val parsed = typesRaw.split(",").map { name ->
                runCatching { PeqFilterType.valueOf(name.trim()) }.getOrDefault(PeqFilterType.PEAK)
            }
            if (parsed.size == 10) parsed else DEFAULT_TEN_BAND_TYPES
        } else DEFAULT_TEN_BAND_TYPES

        val slotsList = mutableListOf<CustomPeqSlot>()
        for (slotIdx in 0..2) {
            val slotRaw = p.getString("peq_custom_slot_$slotIdx", null) ?: continue
            val parts = slotRaw.split("|")
            if (parts.size >= 5) {
                val sName = parts[0]
                val sLevels = parts[1].split(",").mapNotNull { it.toIntOrNull() }
                val sQs = parts[2].split(",").mapNotNull { it.toFloatOrNull() }
                val sHz = parts[3].split(",").mapNotNull { it.toFloatOrNull() }
                val sTypes = parts[4].split(",").map { runCatching { PeqFilterType.valueOf(it) }.getOrDefault(PeqFilterType.PEAK) }
                val sPreamp = parts.getOrNull(5)?.toFloatOrNull() ?: 0f
                val sHpf = parts.getOrNull(6)?.toIntOrNull() ?: 0
                val sLpf = parts.getOrNull(7)?.toIntOrNull() ?: 20000
                if (sLevels.size == 10 && sQs.size == 10 && sHz.size == 10 && sTypes.size == 10) {
                    slotsList.add(
                        CustomPeqSlot(
                            slotIndex = slotIdx,
                            name = sName,
                            tenBandLevels = sLevels,
                            tenBandQFactors = sQs,
                            tenBandCenterHz = sHz,
                            tenBandFilterTypes = sTypes,
                            preampGainDb = sPreamp,
                            highPassHz = sHpf,
                            lowPassHz = sLpf
                        )
                    )
                }
            }
        }

        _state.value = AudioEffectState(
            isEnabled = enabled && activePreset != EffectPreset.OFF,
            currentPreset = activePreset,
            bassBoostStrength = bass,
            bassPunchStrength = punch,
            bassCutoffHz = cutoffHz,
            virtualizerStrength = virt,
            soundstageDepthStrength = depth,
            crossfeedStrength = crossfeed,
            vocalClarityStrength = clarity,
            deEsserStrength = deEsser,
            trebleAirStrength = air,
            analogWarmthStrength = warmth,
            transientAttackStrength = transientAttack,
            surroundRotationSpeed = rotSpeed,
            loudnessGainMb = loudness,
            preampGainDb = preamp,
            channelBalance = balance,
            antiClippingEnabled = antiClip,
            equalLoudnessCompensation = eqLoudness,
            volumeNormalizationEnabled = volNorm,
            reverbMode = reverb,
            reverbWetMix = reverbWet,
            reverbPreDelayMs = reverbPreDelay,
            reverbDamping = reverbDamp,
            bandLevels = bands,
            tenBandLevels = restoredTenBands,
            tenBandQFactors = restoredQs,
            tenBandCenterHz = restoredHz,
            tenBandFilterTypes = restoredTypes,
            highPassCutoffHz = hpfHz,
            lowPassCutoffHz = lpfHz,
            customPeqSlots = slotsList
        )
        dspConfigVersion.incrementAndGet()
    }

    private fun saveToPrefs() {
        val p = prefs ?: return
        val s = _state.value
        val tenBandsSerialized = (0 until 10).joinToString(",") { idx ->
            (s.tenBandLevels.getOrNull(idx) ?: 0).coerceIn(-1000, 1000).toString()
        }
        val qsSerialized = (0 until 10).joinToString(",") { idx ->
            String.format(java.util.Locale.US, "%.2f", (s.tenBandQFactors.getOrNull(idx) ?: 1.20f).coerceIn(0.30f, 8.00f))
        }
        val hzSerialized = (0 until 10).joinToString(",") { idx ->
            String.format(java.util.Locale.US, "%.1f", (s.tenBandCenterHz.getOrNull(idx) ?: DEFAULT_TEN_BAND_HZ[idx]).coerceIn(20f, 20000f))
        }
        val typesSerialized = (0 until 10).joinToString(",") { idx ->
            (s.tenBandFilterTypes.getOrNull(idx) ?: PeqFilterType.PEAK).name
        }
        val editor = p.edit()
            .putBoolean("effect_enabled_v2", s.isEnabled)
            .putString("preset_v2", s.currentPreset.name)
            .putInt("bass_v2", s.bassBoostStrength)
            .putInt("bass_punch_v3", s.bassPunchStrength)
            .putInt("bass_cutoff_v2", s.bassCutoffHz)
            .putInt("virt_v2", s.virtualizerStrength)
            .putInt("depth_v3", s.soundstageDepthStrength)
            .putInt("crossfeed_v3", s.crossfeedStrength)
            .putInt("clarity_v2", s.vocalClarityStrength)
            .putInt("de_esser_v3", s.deEsserStrength)
            .putInt("treble_air_v3", s.trebleAirStrength)
            .putInt("warmth_v2", s.analogWarmthStrength)
            .putInt("transient_attack_v3", s.transientAttackStrength)
            .putInt("rot_speed_v2", s.surroundRotationSpeed)
            .putInt("loudness_v2", s.loudnessGainMb)
            .putFloat("preamp_db_v2", s.preampGainDb)
            .putFloat("channel_balance_v2", s.channelBalance)
            .putBoolean("anti_clip_v2", s.antiClippingEnabled)
            .putBoolean("eq_loudness_comp_v2", s.equalLoudnessCompensation)
            .putBoolean("vol_norm_v2", s.volumeNormalizationEnabled)
            .putString("reverb_v2", s.reverbMode.name)
            .putInt("reverb_wet_v3", s.reverbWetMix)
            .putInt("reverb_predelay_v3", s.reverbPreDelayMs)
            .putInt("reverb_damp_v3", s.reverbDamping)
            .putInt("peq_hpf_v3", s.highPassCutoffHz)
            .putInt("peq_lpf_v3", s.lowPassCutoffHz)
            .putString("bands_v2", serializeBands(s.bandLevels))
            .putString("ten_bands_v3", tenBandsSerialized)
            .putString("ten_band_qs_v3", qsSerialized)
            .putString("ten_band_hz_v3", hzSerialized)
            .putString("ten_band_types_v3", typesSerialized)

        s.customPeqSlots.forEach { slot ->
            val serializedSlot = listOf(
                slot.name.replace("|", " "),
                slot.tenBandLevels.joinToString(","),
                slot.tenBandQFactors.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) },
                slot.tenBandCenterHz.joinToString(",") { String.format(java.util.Locale.US, "%.1f", it) },
                slot.tenBandFilterTypes.joinToString(",") { it.name },
                slot.preampGainDb.toString(),
                slot.highPassHz.toString(),
                slot.lowPassHz.toString()
            ).joinToString("|")
            editor.putString("peq_custom_slot_${slot.slotIndex}", serializedSlot)
        }
        editor.apply()
    }
}
