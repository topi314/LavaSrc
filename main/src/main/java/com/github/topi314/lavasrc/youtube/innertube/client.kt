package com.github.topi314.lavasrc.youtube.innertube

import com.github.topi314.lavalyrics.lyrics.AudioLyrics
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import dev.schlaubi.lyrics.LyricsNotFoundException
import dev.schlaubi.lyrics.internal.model.*
import dev.schlaubi.lyrics.internal.util.*
import dev.schlaubi.lyrics.protocol.Lyrics
import dev.schlaubi.lyrics.protocol.TextLyrics
import dev.schlaubi.lyrics.protocol.TimedLyrics
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.net.URI
import java.time.Duration
import java.util.*

private val json = Json {
    ignoreUnknownKeys = true
}

internal fun HttpInterface.requestMusicAutoComplete(
    input: String,
    locale: Locale? = null
): InnerTubeBox<SearchSuggestionsSectionRendererContent> =
    makeRequest(
        youtubeMusic,
        "music",
        "get_search_suggestions",
        body = MusicSearchRequest(musicContext(locale ?: Locale("en", "US")), input)
    ) {
        if (locale != null) {
            val localeString = if (locale.country != null) {
                "${locale.language}-${locale.country},${locale.language}"
            } else {
                locale.language
            }
            addHeader(HttpHeaders.ACCEPT_LANGUAGE, localeString)
        }
    }

private val emptyTrack = Lyrics.Track("", "", "", emptyList())

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
internal fun HttpInterface.requestLyrics(videoId: String): AudioLyrics {
    val browse =
        makeRequest<_, JsonObject>(youtubeMusic, "next", body = NextRequest(mobileYoutubeMusicContext, videoId))
    val browseId = browse.browseEndpoint ?: throw LyricsNotFoundException()
    val browseResult =
        makeRequest<_, JsonObject>(youtubeMusic, "browse", body = BrowseRequest(mobileYoutubeMusicContext, browseId))
    val lyricsData = browseResult.lyricsData
    val data = if (lyricsData != null) {
        val source = lyricsData.source
        TimedLyrics(emptyTrack, source, lyricsData.lines)
    } else {
        val renderer = browseResult.musicDescriptionShelfRenderer ?: notFound()
        val text = renderer.getRunningText("description")!!
        val source = renderer.getRunningText("footer")!!
        TextLyrics(emptyTrack, source, text)
    }

    return WrappedLyrics(data)
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
internal fun HttpInterface.takeFirstSearchResult(query: String, region: String?): String? {
    val result = makeRequest<_, JsonObject>(
        youtubeMusic,
        "search",
        body = SearchRequest(mobileYoutubeMusicContext(region), query, onlyTracksSearchParam)
    )
    val section = result
        .getJsonObject("contents")
        ?.getJsonObject("tabbedSearchResultsRenderer")
        ?.getJsonArray("tabs")
        ?.getJsonObject(0)
        ?.getJsonObject("tabRenderer")
        ?.getJsonObject("content")
        ?.getJsonObject("sectionListRenderer")
        ?.getJsonArray("contents") ?: JsonArray(emptyList())

    return section
        .firstNotNullOfOrNull {
            it.jsonObject.getJsonObject("musicShelfRenderer")
                ?.getJsonArray("contents")
                ?.firstNotNullOfOrNull { content ->
                    content.jsonObject.getJsonObject("musicTwoColumnItemRenderer")
                        ?.getJsonObject("navigationEndpoint")
                        ?.getJsonObject("watchEndpoint")
                        ?.getString("videoId")
                }
        }
}


@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified B, reified R> HttpInterface.makeRequest(
    domain: URI,
    vararg endpoint: String,
    body: B? = null,
    builder: HttpPost.() -> Unit = {}
): R {
    val uri = URIBuilder(domain)
        .setPathSegments(listOf("youtubei", "v1") + endpoint.asList())
        .addParameter("prettyPrint", "false")
    val post = HttpPost(uri.build()).apply {
        addHeader(HttpHeaders.REFERER, domain.toString())
        if (body != null) {
            addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
            entity = StringEntity(json.encodeToString(body))
        }
        builder()
    }

    val response = execute(post)

    return response.entity.content.buffered().use {
        json.decodeFromStream(it)
    }
}

private class WrappedLyrics(private val lyrics: Lyrics) : AudioLyrics {
    override fun getSourceName(): String = "youtube"

    override fun getProvider(): String = lyrics.source

    override fun getText(): String = lyrics.text

    override fun getLines(): MutableList<AudioLyrics.Line>? = (lyrics as? TimedLyrics)?.lines?.map {
        Line(it)
    }?.toMutableList()

    private class Line(private val line: TimedLyrics.Line) : AudioLyrics.Line {
        override fun getTimestamp(): Duration = Duration.ofMillis(line.range.first)

        override fun getDuration(): Duration = Duration.ofMillis(line.range.last).minus(timestamp)

        override fun getLine(): String = line.line

    }
}
