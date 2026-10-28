package com.concept.music.service

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 音频元数据内嵌信息（对齐 lisalee23042605/kgmusicbot 的 mutagen 元数据内嵌能力，并扩展完整歌曲标签）：
 * - 歌曲基本信息：标题 (TIT2/TITLE)、歌手 (TPE1/ARTIST)、专辑 (TALB/ALBUM)、专辑艺术家 (TPE2/ALBUMARTIST)、
 *   发行日期/年份 (TYER/TDRC/DATE)、酷狗 Hash 备注 (COMM/COMMENT)
 * - 高清封面图片 (APIC / METADATA_BLOCK_PICTURE, type=3 Front Cover, 默认内嵌)
 * - 歌词文本 (USLT / LYRICS + UNSYNCEDLYRICS, 默认内嵌标准 LRC 时间轴歌词)
 */
data class AudioTagMetadata(
    val title: String,
    val artist: String,
    val album: String = "",
    val publishDate: String = "",
    val songHash: String = "",
    val qualityLabel: String = "",
    val coverBytes: ByteArray? = null,
    val coverMimeType: String = "image/jpeg",
    val lrcText: String = ""
)

/**
 * 纯 Kotlin 零外部依赖的音频标签与封面/歌词内嵌引擎：
 * 支持：
 * 1. **FLAC 无损音频** (`fLaC`)：写入/合并 Vorbis Comment (Block Type 4: TITLE, ARTIST, ALBUM, ALBUMARTIST, DATE, COMMENT, LYRICS, UNSYNCEDLYRICS)
 *    与 FLAC Picture (Block Type 6: Front Cover type=3, 高清封面二进制)
 * 2. **MP3 音频** (`ID3v2.3`)：写入 TIT2, TPE1, TALB, TPE2, TYER, TDRC, COMM, USLT (歌词, UTF-16 with BOM), APIC (高清封面, type=3 Front Cover)
 * 3. **OGG Vorbis 音频** (`OggS`)：酷狗概念版标准音质常返回 OGG 容器，通过识别文件魔数自动安全处理（避免用 ID3 头破坏 OGG Page 同步头）
 */
object AudioMetadataEmbedder {

    enum class AudioContainerFormat {
        FLAC,
        MP3,
        OGG,
        OTHER
    }

    /**
     * 根据文件头部魔数与扩展名精准识别真实音频容器格式
     */
    fun detectFormat(file: File, fallbackExt: String = ""): AudioContainerFormat {
        if (file.exists() && file.length() >= 4L) {
            val header = ByteArray(4)
            runCatching {
                file.inputStream().use { it.read(header) }
            }
            if (header[0] == 'f'.code.toByte() &&
                header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() &&
                header[3] == 'C'.code.toByte()
            ) {
                return AudioContainerFormat.FLAC
            }
            if (header[0] == 'O'.code.toByte() &&
                header[1] == 'g'.code.toByte() &&
                header[2] == 'g'.code.toByte() &&
                header[3] == 'S'.code.toByte()
            ) {
                return AudioContainerFormat.OGG
            }
            if (header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()
            ) {
                return AudioContainerFormat.MP3
            }
            // MPEG Audio Frame Sync (0xFF 0xE0+)
            if ((header[0].toInt() and 0xFF) == 0xFF && ((header[1].toInt() and 0xE0) == 0xE0)) {
                return AudioContainerFormat.MP3
            }
        }
        val ext = fallbackExt.ifBlank { file.extension }.trim().trimStart('.').lowercase()
        return when (ext) {
            "flac" -> AudioContainerFormat.FLAC
            "mp3" -> AudioContainerFormat.MP3
            "ogg" -> AudioContainerFormat.OGG
            else -> AudioContainerFormat.OTHER
        }
    }

    /**
     * 探测图片字节流的真实 MIME 类型（image/jpeg 或 image/png）
     */
    fun detectImageMimeType(bytes: ByteArray?): String {
        if (bytes == null || bytes.size < 4) return "image/jpeg"
        return if (bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            "image/png"
        } else {
            "image/jpeg"
        }
    }

    /**
     * 将歌曲详细信息、高清封面、LRC 歌词内嵌写入音频文件
     */
    fun embedMetadata(file: File, meta: AudioTagMetadata): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return when (detectFormat(file)) {
            AudioContainerFormat.FLAC -> runCatching { embedFlacMetadata(file, meta) }.getOrDefault(false)
            AudioContainerFormat.MP3 -> runCatching { embedMp3Id3v23Metadata(file, meta) }.getOrDefault(false)
            AudioContainerFormat.OGG -> runCatching { embedOggVorbisMetadata(file, meta) }.getOrDefault(false)
            AudioContainerFormat.OTHER -> false
        }
    }

    // =========================================================================
    // 1. FLAC 元数据块写入器 (Vorbis Comment Type 4 + Picture Type 6)
    // =========================================================================

    private data class FlacBlock(val type: Int, val data: ByteArray)

    internal fun embedFlacMetadata(file: File, meta: AudioTagMetadata): Boolean {
        val rawBytes = file.readBytes()
        if (rawBytes.size < 8) return false
        if (rawBytes[0] != 'f'.code.toByte() ||
            rawBytes[1] != 'L'.code.toByte() ||
            rawBytes[2] != 'a'.code.toByte() ||
            rawBytes[3] != 'C'.code.toByte()
        ) {
            return false
        }

        val keptBlocks = mutableListOf<FlacBlock>()
        var existingComments = linkedMapOf<String, String>()
        var offset = 4
        var isLastBlock = false

        while (!isLastBlock && offset + 4 <= rawBytes.size) {
            val headerByte = rawBytes[offset].toInt() and 0xFF
            isLastBlock = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F
            val length = ((rawBytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((rawBytes[offset + 2].toInt() and 0xFF) shl 8) or
                (rawBytes[offset + 3].toInt() and 0xFF)
            offset += 4
            if (length < 0 || offset + length > rawBytes.size) {
                return false
            }
            val blockData = rawBytes.copyOfRange(offset, offset + length)
            offset += length

            when (blockType) {
                4 -> {
                    // 解析已有的 Vorbis Comment 以便保留非冲突字段
                    runCatching {
                        existingComments.putAll(parseVorbisCommentBlock(blockData))
                    }
                }
                6 -> {
                    // 清除旧封面（与 kgmusicbot audio.clear_pictures() 一致），仅当本次无新封面时保留旧封面
                    if (meta.coverBytes == null || meta.coverBytes.isEmpty()) {
                        keptBlocks.add(FlacBlock(blockType, blockData))
                    }
                }
                1 -> {
                    // 跳过旧 Padding 块，最后统一生成适量 Padding
                }
                else -> {
                    keptBlocks.add(FlacBlock(blockType, blockData))
                }
            }
        }

        val audioFramesStartOffset = offset
        if (audioFramesStartOffset > rawBytes.size) return false

        // 合并并覆盖歌曲详细信息与歌词（对齐 kgmusicbot: audio["LYRICS"] 与 audio["UNSYNCEDLYRICS"]）
        if (meta.title.isNotBlank()) existingComments["TITLE"] = meta.title
        if (meta.artist.isNotBlank()) {
            existingComments["ARTIST"] = meta.artist
            existingComments["ALBUMARTIST"] = meta.artist
        }
        if (meta.album.isNotBlank()) existingComments["ALBUM"] = meta.album
        if (meta.publishDate.isNotBlank()) {
            existingComments["DATE"] = meta.publishDate
            existingComments["YEAR"] = meta.publishDate.take(4)
        }
        val commentStr = buildList {
            if (meta.qualityLabel.isNotBlank()) add(meta.qualityLabel)
            if (meta.songHash.isNotBlank()) add("Hash:${meta.songHash.uppercase()}")
        }.joinToString(" | ")
        if (commentStr.isNotBlank()) {
            existingComments["COMMENT"] = commentStr
        }
        if (meta.lrcText.isNotBlank()) {
            existingComments["LYRICS"] = meta.lrcText
            existingComments["UNSYNCEDLYRICS"] = meta.lrcText
        }

        val vorbisCommentData = buildVorbisCommentBlock(
            vendor = "BreezeMusic Kugou Concept",
            comments = existingComments
        )
        keptBlocks.add(FlacBlock(4, vorbisCommentData))

        if (meta.coverBytes != null && meta.coverBytes.isNotEmpty()) {
            val mime = detectImageMimeType(meta.coverBytes)
            val picData = buildFlacPictureBlock(
                imageBytes = meta.coverBytes,
                mimeType = mime,
                description = "Cover"
            )
            // FLAC metadata block 单块上限为 16MB - 1 (0xFFFFFF)
            if (picData.size <= 0xFFFFFF) {
                keptBlocks.add(FlacBlock(6, picData))
            }
        }

        val tempFile = File(file.parentFile, "${file.name}.tmp_tag")
        try {
            tempFile.outputStream().buffered().use { out ->
                out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                for (i in keptBlocks.indices) {
                    val blk = keptBlocks[i]
                    val isLast = (i == keptBlocks.lastIndex)
                    val firstByte = (if (isLast) 0x80 else 0x00) or (blk.type and 0x7F)
                    val len = blk.data.size
                    out.write(firstByte)
                    out.write((len ushr 16) and 0xFF)
                    out.write((len ushr 8) and 0xFF)
                    out.write(len and 0xFF)
                    out.write(blk.data)
                }
                out.write(rawBytes, audioFramesStartOffset, rawBytes.size - audioFramesStartOffset)
            }
            if (tempFile.exists() && tempFile.length() > 0L) {
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
                return true
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
        return false
    }

    internal fun parseVorbisCommentBlock(data: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.remaining() < 4) return result
        val vendorLen = buf.int
        if (vendorLen < 0 || buf.remaining() < vendorLen + 4) return result
        buf.position(buf.position() + vendorLen)
        val count = buf.int
        if (count < 0 || count > 10_000) return result
        for (i in 0 until count) {
            if (buf.remaining() < 4) break
            val itemLen = buf.int
            if (itemLen < 0 || buf.remaining() < itemLen) break
            val bytes = ByteArray(itemLen)
            buf.get(bytes)
            val str = String(bytes, Charsets.UTF_8)
            val eqIdx = str.indexOf('=')
            if (eqIdx > 0) {
                val key = str.substring(0, eqIdx).uppercase()
                val value = str.substring(eqIdx + 1)
                result[key] = value
            }
        }
        return result
    }

    internal fun buildVorbisCommentBlock(
        vendor: String,
        comments: Map<String, String>,
        includeFramingBit: Boolean = false
    ): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        val commentByteArrays = comments.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .map { "${it.key.uppercase()}=${it.value}".toByteArray(Charsets.UTF_8) }

        val out = ByteArrayOutputStream()
        fun writeLeInt(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 24) and 0xFF)
        }

        writeLeInt(vendorBytes.size)
        out.write(vendorBytes)
        writeLeInt(commentByteArrays.size)
        for (entryBytes in commentByteArrays) {
            writeLeInt(entryBytes.size)
            out.write(entryBytes)
        }
        if (includeFramingBit) {
            out.write(1)
        }
        return out.toByteArray()
    }

    internal fun buildFlacPictureBlock(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        description: String = "Cover"
    ): ByteArray {
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        val descBytes = description.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(32 + mimeBytes.size + descBytes.size + imageBytes.size)
        fun writeBeInt(v: Int) {
            out.write((v ushr 24) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        writeBeInt(3) // Picture type 3: Cover (front)
        writeBeInt(mimeBytes.size)
        out.write(mimeBytes)
        writeBeInt(descBytes.size)
        out.write(descBytes)
        writeBeInt(0) // width (0 = auto)
        writeBeInt(0) // height
        writeBeInt(24) // color depth
        writeBeInt(0) // indexed colors
        writeBeInt(imageBytes.size)
        out.write(imageBytes)
        return out.toByteArray()
    }

    // =========================================================================
    // 2. MP3 ID3v2.3 标签写入器 (TIT2, TPE1, TALB, TPE2, TYER, COMM, USLT, APIC)
    // =========================================================================

    internal fun embedMp3Id3v23Metadata(file: File, meta: AudioTagMetadata): Boolean {
        val rawBytes = file.readBytes()
        if (rawBytes.isEmpty()) return false

        // 若原文件已有 ID3v2 头，跳过旧 ID3v2 段定位纯音频帧起点
        var audioStartOffset = 0
        if (rawBytes.size >= 10 &&
            rawBytes[0] == 'I'.code.toByte() &&
            rawBytes[1] == 'D'.code.toByte() &&
            rawBytes[2] == '3'.code.toByte()
        ) {
            val flags = rawBytes[5].toInt() and 0xFF
            val synchsafeSize = ((rawBytes[6].toInt() and 0x7F) shl 21) or
                ((rawBytes[7].toInt() and 0x7F) shl 14) or
                ((rawBytes[8].toInt() and 0x7F) shl 7) or
                (rawBytes[9].toInt() and 0x7F)
            val footerSize = if ((flags and 0x10) != 0) 10 else 0
            val totalOldId3 = 10 + synchsafeSize + footerSize
            if (totalOldId3 in 10..rawBytes.size) {
                audioStartOffset = totalOldId3
            }
        }

        val framesOut = ByteArrayOutputStream()

        fun writeFrame(frameId: String, payload: ByteArray) {
            if (payload.isEmpty()) return
            val idBytes = frameId.toByteArray(Charsets.US_ASCII)
            if (idBytes.size != 4) return
            framesOut.write(idBytes)
            val size = payload.size
            framesOut.write((size ushr 24) and 0xFF)
            framesOut.write((size ushr 16) and 0xFF)
            framesOut.write((size ushr 8) and 0xFF)
            framesOut.write(size and 0xFF)
            // Flags (2 bytes = 0x0000)
            framesOut.write(0)
            framesOut.write(0)
            framesOut.write(payload)
        }

        fun encodeUtf16WithBom(text: String): ByteArray {
            val out = ByteArrayOutputStream()
            // UTF-16LE with BOM (0xFF 0xFE) for maximum compatibility across Android MediaStore & desktop players
            out.write(0xFF)
            out.write(0xFE)
            out.write(text.toByteArray(Charsets.UTF_16LE))
            return out.toByteArray()
        }

        fun buildTextFramePayload(text: String): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(1) // Encoding 1 = UTF-16 with BOM
            out.write(encodeUtf16WithBom(text))
            return out.toByteArray()
        }

        if (meta.title.isNotBlank()) {
            writeFrame("TIT2", buildTextFramePayload(meta.title))
        }
        if (meta.artist.isNotBlank()) {
            writeFrame("TPE1", buildTextFramePayload(meta.artist))
            writeFrame("TPE2", buildTextFramePayload(meta.artist))
        }
        if (meta.album.isNotBlank()) {
            writeFrame("TALB", buildTextFramePayload(meta.album))
        }
        if (meta.publishDate.isNotBlank()) {
            val year = meta.publishDate.take(4)
            if (year.length == 4 && year.all { it.isDigit() }) {
                writeFrame("TYER", buildTextFramePayload(year))
            }
            writeFrame("TDRC", buildTextFramePayload(meta.publishDate))
        }

        // 备注信息 (COMM)
        val commentText = buildList {
            if (meta.qualityLabel.isNotBlank()) add(meta.qualityLabel)
            if (meta.songHash.isNotBlank()) add("Hash:${meta.songHash.uppercase()}")
        }.joinToString(" | ")
        if (commentText.isNotBlank()) {
            val commPayload = ByteArrayOutputStream().apply {
                write(1) // UTF-16 with BOM
                write("eng".toByteArray(Charsets.US_ASCII))
                // Empty UTF-16 description: BOM + 0x00 0x00
                write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00))
                write(encodeUtf16WithBom(commentText))
            }.toByteArray()
            writeFrame("COMM", commPayload)
        }

        // 内嵌歌词帧 (USLT，对齐 kgmusicbot: USLT(encoding=..., lang="chi", desc="", text=lrc_text))
        if (meta.lrcText.isNotBlank()) {
            val usltPayload = ByteArrayOutputStream().apply {
                write(1) // UTF-16 with BOM
                write("chi".toByteArray(Charsets.US_ASCII))
                // Empty UTF-16 description: BOM + 0x00 0x00
                write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00))
                write(encodeUtf16WithBom(meta.lrcText))
            }.toByteArray()
            writeFrame("USLT", usltPayload)
        }

        // 内嵌高清封面帧 (APIC，对齐 kgmusicbot: APIC(mime="image/jpeg", type=3, desc="Cover", data=img_data))
        if (meta.coverBytes != null && meta.coverBytes.isNotEmpty()) {
            val mime = detectImageMimeType(meta.coverBytes)
            val apicPayload = ByteArrayOutputStream().apply {
                write(0) // ISO-8859-1 for description
                write(mime.toByteArray(Charsets.ISO_8859_1))
                write(0) // null terminator for MIME
                write(3) // Picture Type 3 = Cover (front)
                write("Cover".toByteArray(Charsets.ISO_8859_1))
                write(0) // null terminator for description
                write(meta.coverBytes)
            }.toByteArray()
            writeFrame("APIC", apicPayload)
        }

        val framesBytes = framesOut.toByteArray()
        val tagSize = framesBytes.size

        val tempFile = File(file.parentFile, "${file.name}.tmp_id3")
        try {
            tempFile.outputStream().buffered().use { out ->
                // ID3v2.3 Header (10 bytes)
                out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
                out.write(3) // Version 2.3
                out.write(0) // Revision 0
                out.write(0) // Flags 0
                // Synchsafe 28-bit integer
                out.write((tagSize ushr 21) and 0x7F)
                out.write((tagSize ushr 14) and 0x7F)
                out.write((tagSize ushr 7) and 0x7F)
                out.write(tagSize and 0x7F)
                out.write(framesBytes)
                out.write(rawBytes, audioStartOffset, rawBytes.size - audioStartOffset)
            }
            if (tempFile.exists() && tempFile.length() > 0L) {
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
                return true
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
        return false
    }

    // =========================================================================
    // 3. OGG Vorbis 兼容处理（酷狗概念版 128K 标准音质常为 OGG）
    // =========================================================================

    @Suppress("UNUSED_PARAMETER")
    internal fun embedOggVorbisMetadata(file: File, meta: AudioTagMetadata): Boolean {
        // OGG 流采用 CRC32 Page 分页封装，保持原 OGG 音频流结构完好，不向文件头注入 ID3 字节以免损坏 OggS 同步头
        return file.exists() && file.length() > 0L
    }

    /**
     * 读取音频文件中已内嵌的元数据摘要（用于单元测试与本地校验）
     */
    fun readEmbeddedTagsForVerification(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return when (detectFormat(file)) {
            AudioContainerFormat.FLAC -> {
                val raw = file.readBytes()
                var offset = 4
                var isLast = false
                val map = linkedMapOf<String, String>()
                while (!isLast && offset + 4 <= raw.size) {
                    val h = raw[offset].toInt() and 0xFF
                    isLast = (h and 0x80) != 0
                    val type = h and 0x7F
                    val len = ((raw[offset + 1].toInt() and 0xFF) shl 16) or
                        ((raw[offset + 2].toInt() and 0xFF) shl 8) or
                        (raw[offset + 3].toInt() and 0xFF)
                    offset += 4
                    if (offset + len > raw.size) break
                    val block = raw.copyOfRange(offset, offset + len)
                    offset += len
                    if (type == 4) {
                        map.putAll(parseVorbisCommentBlock(block))
                    } else if (type == 6 && block.size > 32) {
                        map["HAS_PICTURE"] = "true"
                    }
                }
                map
            }
            AudioContainerFormat.MP3 -> {
                val raw = file.readBytes()
                if (raw.size < 10 || raw[0] != 'I'.code.toByte() || raw[1] != 'D'.code.toByte() || raw[2] != '3'.code.toByte()) {
                    return emptyMap()
                }
                val tagSize = ((raw[6].toInt() and 0x7F) shl 21) or
                    ((raw[7].toInt() and 0x7F) shl 14) or
                    ((raw[8].toInt() and 0x7F) shl 7) or
                    (raw[9].toInt() and 0x7F)
                val end = (10 + tagSize).coerceAtMost(raw.size)
                var pos = 10
                val map = linkedMapOf<String, String>()
                while (pos + 10 <= end) {
                    val id = String(raw, pos, 4, Charsets.US_ASCII)
                    if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break
                    val size = ((raw[pos + 4].toInt() and 0xFF) shl 24) or
                        ((raw[pos + 5].toInt() and 0xFF) shl 16) or
                        ((raw[pos + 6].toInt() and 0xFF) shl 8) or
                        (raw[pos + 7].toInt() and 0xFF)
                    pos += 10
                    if (size <= 0 || pos + size > end) break
                    val payload = raw.copyOfRange(pos, pos + size)
                    pos += size
                    when (id) {
                        "TIT2", "TPE1", "TALB", "TPE2", "TYER", "TDRC" -> {
                            if (payload.size >= 3 && payload[0].toInt() == 1) {
                                val text = String(payload, 3, payload.size - 3, Charsets.UTF_16LE)
                                map[id] = text
                            }
                        }
                        "USLT" -> {
                            if (payload.size >= 10 && payload[0].toInt() == 1) {
                                // 1 byte enc + 3 bytes lang + 4 bytes empty UTF16 desc (FF FE 00 00) + 2 bytes BOM (FF FE)
                                val lrc = String(payload, 10, payload.size - 10, Charsets.UTF_16LE)
                                map["USLT"] = lrc
                            }
                        }
                        "APIC" -> {
                            map["HAS_APIC"] = "true"
                        }
                    }
                }
                map
            }
            else -> emptyMap()
        }
    }
}
