package com.github.topi314.lavasrc.youtube.innertube

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.net.URI
import java.util.*

private val json = Json {
    ignoreUnknownKeys = true
}

fun HttpInterface.requestMusicAutoComplete(
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
    val jsonText = response.entity.content.buffered().readAllBytes().decodeToString()

    return json.decodeFromString(jsonText)
}
