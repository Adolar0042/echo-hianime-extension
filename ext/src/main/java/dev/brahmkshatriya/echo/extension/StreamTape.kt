package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Streamable

class StreamTape : Extractor() {
    private val linkRegex = Regex("""'robotlink'\)\.innerHTML = '(.+?)'\+ \('(.+?)'\)""")

    override suspend fun extract(link: String): List<Streamable> {
        val reg = linkRegex.find(client.get(link.replace("tape.com", "adblocker.xyz")).text)
            ?: return listOf()

        val extractedUrl = "https:${reg.groups[1]!!.value + reg.groups[2]!!.value.substring(3)}"
        return listOf(
            Streamable(
                id = extractedUrl,
                quality = 9001, // over 9000, hehe, get it?
                mediaType = Streamable.MediaType.AudioVideo,
                mimeType = Streamable.MimeType.Progressive,
            )
        )
    }
}