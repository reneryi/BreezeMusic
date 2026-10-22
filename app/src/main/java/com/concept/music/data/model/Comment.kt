package com.concept.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SongComment(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String,
    val content: String,
    val timeFormatted: String,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val isHot: Boolean = false,
    val vipLevel: Int = 0,
    val ipLocation: String = "",
    val parentId: String? = null,
    val rootId: String? = null,
    val replyUserName: String? = null,
    val replyCount: Int = 0,
    val replies: List<SongComment> = emptyList(),
    val images: List<String> = emptyList(),
    val timestampMs: Long = 0L
)

@Immutable
data class SongDetailInfo(
    val song: Song,
    val albumName: String = "",
    val singerName: String = "",
    val publishTime: String = "",
    val company: String = "",
    val audioQuality: String = "SQ 无损 (FLAC)",
    val sampleRate: String = "44.1 kHz / 16 bit",
    val bitRate: String = "920 kbps",
    val fileSize: String = "28.5 MB",
    val fileHash: String = ""
)

/**
 * 酷狗评论内置表情编码 ([em]eXXXXX[/em] 及中文方括号表情标签) 转原生 Emoji 映射工具
 */
object KugouCommentEmojiParser {
    private val codeToEmoji = mapOf(
        "e400001" to "😊", "e400002" to "😍", "e400003" to "😭", "e400004" to "😡",
        "e400005" to "😳", "e400006" to "😎", "e400007" to "😂", "e400008" to "🥺",
        "e400009" to "👍", "e400010" to "❤️", "e400011" to "💔", "e400012" to "🌹",
        "e400013" to "🎉", "e400014" to "🙏", "e400015" to "🎧", "e400016" to "🎵",
        "e400017" to "🔥", "e400018" to "✨", "e400019" to "🌙", "e400020" to "🍻",
        "e400021" to "🤭", "e400022" to "🤔", "e400023" to "😴", "e400024" to "🤗",
        "e400025" to "😘", "e400026" to "🥳", "e400027" to "🤩", "e400028" to "😇",
        "e400029" to "🙌", "e400030" to "👏", "e400031" to "💪", "e400032" to "🌟",
        "e100" to "😊", "e101" to "😄", "e102" to "😂", "e103" to "😭",
        "e104" to "😍", "e105" to "😘", "e106" to "🥺", "e107" to "😎",
        "e108" to "👍", "e109" to "❤️", "e110" to "🌹", "e111" to "🙏"
    )

    private val tagToEmoji = mapOf(
        "[微笑]" to "😊", "[撇嘴]" to "😒", "[色]" to "😍", "[发呆]" to "😳",
        "[得意]" to "😎", "[流泪]" to "😢", "[害羞]" to "☺️", "[闭嘴]" to "🤐",
        "[睡]" to "😴", "[大哭]" to "😭", "[尴尬]" to "😅", "[发怒]" to "😡",
        "[调皮]" to "😜", "[呲牙]" to "😁", "[惊讶]" to "😲", "[难过]" to "😔",
        "[酷]" to "😎", "[冷汗]" to "😰", "[抓狂]" to "😫", "[吐]" to "🤮",
        "[偷笑]" to "🤭", "[愉快]" to "😄", "[白眼]" to "🙄", "[傲慢]" to "😤",
        "[饥饿]" to "🤤", "[困]" to "🥱", "[惊恐]" to "😱", "[流汗]" to "😓",
        "[憨笑]" to "😆", "[悠闲]" to "😌", "[奋斗]" to "💪", "[咒骂]" to "🤬",
        "[疑问]" to "🤔", "[嘘]" to "🤫", "[晕]" to "😵", "[疯了]" to "🤪",
        "[衰]" to "😖", "[骷髅]" to "💀", "[敲打]" to "🔨", "[再见]" to "👋",
        "[擦汗]" to "😅", "[抠鼻]" to "🤏", "[鼓掌]" to "👏", "[糗大了]" to "😳",
        "[坏笑]" to "😏", "[左哼哼]" to "😤", "[右哼哼]" to "😤", "[哈欠]" to "🥱",
        "[鄙视]" to "😒", "[委屈]" to "🥺", "[快哭了]" to "😢", "[阴险]" to "😏",
        "[亲亲]" to "😘", "[吓]" to "😱", "[可怜]" to "🥺", "[菜刀]" to "🔪",
        "[西瓜]" to "🍉", "[啤酒]" to "🍺", "[篮球]" to "🏀", "[乒乓]" to "🏓",
        "[咖啡]" to "☕", "[饭]" to "🍚", "[猪头]" to "🐷", "[玫瑰]" to "🌹",
        "[凋谢]" to "🥀", "[嘴唇]" to "💋", "[爱心]" to "❤️", "[心碎]" to "💔",
        "[蛋糕]" to "🎂", "[闪电]" to "⚡", "[炸弹]" to "💣", "[刀]" to "🗡️",
        "[足球]" to "⚽", "[瓢虫]" to "🐞", "[便便]" to "💩", "[月亮]" to "🌙",
        "[太阳]" to "☀️", "[礼物]" to "🎁", "[拥抱]" to "🤗", "[强]" to "👍",
        "[弱]" to "👎", "[握手]" to "🤝", "[胜利]" to "✌️", "[抱拳]" to "🙏",
        "[勾引]" to "👉", "[拳头]" to "✊", "[差劲]" to "👎", "[爱你]" to "🤟",
        "[NO]" to "🙅", "[OK]" to "👌", "[赞]" to "👍", "[心]" to "❤️",
        "[笑哭]" to "😂", "[捂脸]" to "🤦", "[破涕为笑]" to "😂", "[机智]" to "🤓",
        "[耶]" to "✌️", "[奸笑]" to "😏", "[嘿哈]" to "😆", "[皱眉]" to "😟",
        "[吃瓜]" to "🍉", "[加油]" to "💪", "[汗]" to "😅", "[天啊]" to "😱",
        "[Emm]" to "🤔", "[社会社会]" to "😎", "[旺柴]" to "🐶", "[好的]" to "👌",
        "[打call]" to "📣", "[666]" to "🤙", "[翻白眼]" to "🙄", "[比心]" to "🫰",
        "[裂开]" to "🫠", "[苦涩]" to "🥹", "[叹气]" to "😮‍💨", "[让我看看]" to "👀"
    )

    private val emTagRegex = Regex("""\[em\]([a-zA-Z0-9_]+)\[/em\]""", RegexOption.IGNORE_CASE)
    private val fallbackEmojiPool = listOf("😊", "😂", "😭", "😍", "🥺", "👍", "❤️", "🌹", "🎧", "🎵", "✨", "🔥")

    fun parseEmojiContent(raw: String): String = formatContent(raw)

    fun formatContent(raw: String): String {
        if (raw.isBlank()) return ""
        var text = emTagRegex.replace(raw) { match ->
            val code = match.groupValues[1].lowercase()
            codeToEmoji[code] ?: fallbackEmojiPool[kotlin.math.abs(code.hashCode()) % fallbackEmojiPool.size]
        }
        for ((tag, emoji) in tagToEmoji) {
            if (text.contains(tag)) {
                text = text.replace(tag, emoji)
            }
        }
        return text.trim()
    }
}
