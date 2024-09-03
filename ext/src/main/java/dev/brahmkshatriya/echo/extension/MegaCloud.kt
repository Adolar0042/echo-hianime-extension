package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Streamable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class MegaCloud : Extractor() {
    override suspend fun extract(link: String): List<Streamable> {
        val id = link.substringAfterLast('/').substringBefore('?')
        val url: HttpUrl = link.toHttpUrl()
        val baseUrl = "${url.scheme}://${url.host}"
        println("$baseUrl/embed-2/ajax/e-1/getSources?id=$id")
        val sources =
            client.get("$baseUrl/embed-2/ajax/e-1/getSources?id=$id").parsed<MegaCloudSource>()
        val videos: List<Streamable> = sources.sources.map {
            val type = when (it.type) {
                "hls" -> Streamable.MimeType.HLS
                else -> Streamable.MimeType.Progressive
            }
            Streamable(
                id = it.file,
                quality = 9001, // over 9000, hehe, get it?
                mediaType = Streamable.MediaType.AudioVideo,
                mimeType = type,
//                extra = ,
            )
        }
        val subtitles: List<Streamable> = sources.tracks.mapNotNull {
            if (it.kind == "thumbnails") {
                return@mapNotNull null
            }
            if (it.label != "English") {
                return@mapNotNull null
            }
            Streamable.subtitle(
                id = it.file,
                title = it.label,
//                extra = mapOf(
//                    "kind" to it.kind,
//                )
            )
        }
        return videos + subtitles
    }
}