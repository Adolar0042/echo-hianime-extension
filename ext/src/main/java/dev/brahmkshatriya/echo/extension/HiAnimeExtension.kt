package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.SearchClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.MediaItemsContainer
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toAudioVideoMedia
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toSubtitleMedia
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale

class HiAnimeExtension : ExtensionClient, SearchClient, AlbumClient, TrackClient, HomeFeedClient {
    private val hostUrl = "https://hianime.to"

    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting> = emptyList()

    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    override suspend fun deleteSearchHistory(query: QuickSearchItem.SearchQueryItem) {
        // do nothing
    }

    override suspend fun quickSearch(query: String?): List<QuickSearchItem> {
        val jsonResponse =
            client.get("$hostUrl/ajax/search/suggest?keyword=$query").text

        val res = Json.decodeFromString<HiAnimeAjaxResponse>(jsonResponse)

        return Jsoup.parse(res.html).select(".nav-item").mapNotNull {
            if (it.attr("href").contains(Regex("/search\\?keyword=.*"))) {
                return@mapNotNull null
            }
            val link = hostUrl + "/watch" + it.attr("href").substringBeforeLast('?')
            val cover = it.select(".film-poster img").attr("data-src")
            val title = it.select(".srp-detail .film-name").text()
            val subtitle = it.select(".film-infor").text()

            QuickSearchItem.SearchMediaItem(
                Album(
                    id = link,
                    title = title,
                    cover = cover.toImageHolder(),
                    subtitle = subtitle
                ).toMediaItem()
            )
        }
    }

    override fun searchFeed(query: String?, tab: Tab?) = PagedData.Single<MediaItemsContainer> {
        query ?: return@Single listOf()
        client.get("$hostUrl/search?keyword=${encode(query)}").document
            .select(".film_list-wrap > .flw-item").map {
                val href = it.select(".film-poster > .film-poster-ahref").attr("href").toString()
                val title = it.select(".film-detail > .film-name").text()
                val cover = it.select("div.film-poster img.film-poster-img").attr("data-src")
                val total = it.select(".film-poster > .tick > .tick-sub").text().toInt()
                val subtitle = it.select(".fd-infor").text()

                val link = hostUrl + href
                Album(
                    id = link,
                    title = title,
                    cover = cover.toImageHolder(),
                    tracks = total,
                    subtitle = subtitle
                ).toMediaItem().toMediaItemsContainer()
            }
    }

    override suspend fun searchTabs(query: String?): List<Tab> {
        // not needed
        return listOf()
    }

    // get related stuff
    override fun getMediaItems(album: Album): PagedData<MediaItemsContainer> {
        return PagedData.Single { listOf() }
    }

    // load full album data
    // for example description
    override suspend fun loadAlbum(album: Album): Album {
        val res = client.get(album.id).document.select(".film-description .text").eachText()[0]
        return album.copy(
            description = res
        )
    }

    override fun loadTracks(album: Album) = PagedData.Single<Track> {
        // get eps
        val jsonResponse =
            client.get("$hostUrl/ajax/v2/episode/list/" + album.id.substringAfterLast('-')).text

        val episodeList = Json.decodeFromString<HiAnimeAjaxResponse>(jsonResponse)

        val document: Document = Jsoup.parse(episodeList.html)

        document.select(".ssl-item.ep-item").map {
            val episodeLink = it.attr("href")
            val episodeTitle = it.attr("title")
            Track(
                id = episodeLink,
                title = episodeTitle,
                cover = album.cover,
            )
        }
    }

    // get related media
    override fun getMediaItems(track: Track): PagedData<MediaItemsContainer> {
        return PagedData.Single { listOf() }
    }

    override suspend fun getStreamableMedia(streamable: Streamable): Streamable.Media {
        return when (streamable.mediaType) {
            Streamable.MediaType.AudioVideo -> streamable.id.toAudioVideoMedia()
            Streamable.MediaType.Subtitle -> streamable.id.toSubtitleMedia(Streamable.SubtitleType.VTT)
            else -> throw IllegalStateException()
        }
    }

    override suspend fun loadTrack(track: Track): Track {
        // get servers
        val epID = track.id.substringAfterLast("ep=").substringBefore("&")
        val serversRes = client.get("$hostUrl/ajax/v2/episode/servers?episodeId=$epID").text
        val serverList = Json.decodeFromString<HiAnimeAjaxResponse>(serversRes)
        val document: Document = Jsoup.parse(serverList.html)
        val servers = document.select(".server-item").mapNotNull {
            if (it.attr("data-type") == "dub") return@mapNotNull null // TODO
            val serverName = it.select("a").text() + " (" + it.attr("data-type").uppercase(
                Locale.getDefault()
            ) + ")"
            val res = client.get("$hostUrl/ajax/v2/episode/sources?id=" + it.attr("data-id"))
                .parsed<HiAnimeSource>()
            when (res.link.toHttpUrl().host) {
                "megacloud.tv" -> MegaCloud().extract(res.link)
                "watchsb.com" -> return@mapNotNull null
                "streamtape.com" -> StreamTape().extract(res.link)
                else -> return@mapNotNull null
            }
        }
        return track.copy(
            streamables = servers.flatten()
        )
    }

    override fun getHomeFeed(tab: Tab?): PagedData<MediaItemsContainer> = PagedData.Single {
        client.get("$hostUrl/home").document.select(".anif-blocks .anif-block").map {
            val title = it.select(".anif-block-header").text()
            val anime: List<EchoMediaItem.Lists.AlbumItem> = it.select("li").map { animeEntry ->
                val cover = animeEntry.select(".film-poster .film-poster-img").attr("data-src")
                val link = hostUrl + "/watch" + animeEntry.select(".film-poster a").attr("href")
                val animeTitle = animeEntry.select(".film-detail .film-name .dynamic-name").text()
                val subtitle = animeEntry.select(".film-detail .fd-infor .fdi-item").text()
                val tracks = animeEntry.select(".film-detail .fd-infor .tick-sub").text().toInt()

                Album(
                    id = link,
                    title = animeTitle,
                    cover = cover.toImageHolder(),
                    subtitle = subtitle,
                    tracks = tracks,
                ).toMediaItem()
            }
            MediaItemsContainer.Category(
                title = title,
                list = anime
            )
        }
    }

    override suspend fun getHomeTabs(): List<Tab> {
        return emptyList()
    }
}

@Serializable
data class HiAnimeAjaxResponse(
    val status: Boolean,
    val html: String,
    val totalItems: Int? = null,
    val continueWatch: String? = null
)

@Serializable
data class HiAnimeSource(
    val type: String,
    val link: String,
    val server: Int,
    val sources: List<String>? = null,
    val tracks: List<String>? = null,
    val htmlGuide: String
)

@Serializable
data class MegaCloudSource(
    val sources: List<MegaCloudStream>,
    val tracks: List<MegaCloudSubtitle>,
    val encrypted: Boolean,
    val intro: MegaCloudSkip,
    val outro: MegaCloudSkip,
    val server: Int
)

@Serializable
data class MegaCloudStream(
    val file: String,
    val type: String,
)

@Serializable
data class MegaCloudSubtitle(
    val file: String,
    val label: String = "",
    val kind: String,
    val default: Boolean? = null
)

@Serializable
class MegaCloudSkip(
    val start: Int,
    val end: Int
)