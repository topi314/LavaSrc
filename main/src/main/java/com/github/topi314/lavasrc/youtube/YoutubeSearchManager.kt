package com.github.topi314.lavasrc.youtube

import com.github.topi314.lavasrc.search.SearchResult
import com.github.topi314.lavasrc.search.SearchSourceManager
import com.github.topi314.lavasrc.search.item.SearchAlbum
import com.github.topi314.lavasrc.search.item.SearchArtist
import com.github.topi314.lavasrc.search.item.SearchText
import com.github.topi314.lavasrc.search.item.SearchTrack
import com.github.topi314.lavasrc.youtube.innertube.MusicResponsiveListItemRenderer
import com.github.topi314.lavasrc.youtube.innertube.requestMusicAutoComplete
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.github.topi314.lavasrc.youtube.innertube.MusicResponsiveListItemRenderer.NavigationEndpoint.BrowseEndpoint.Configs.Config.Type as PageType

private fun MusicResponsiveListItemRenderer.NavigationEndpoint.toUrl() = when {
    browseEndpoint != null -> when (browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType) {
        PageType.MUSIC_PAGE_TYPE_ALBUM -> "https://music.youtube.com/browse/${browseEndpoint.browseId}"
        PageType.MUSIC_PAGE_TYPE_ARTIST -> "https://music.youtube.com/channel/${browseEndpoint.browseId}"
    }

    watchEndpoint != null -> "https://music.youtube.com/watch?v${watchEndpoint.videoId}"
    else -> error("Unknown endpoint: $this")
}

class YoutubeSearchManager : SearchSourceManager {
    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
    override fun getSourceName(): String = "youtube"

    override fun loadSearch(query: String, types: List<String>): SearchResult {
        val result = httpInterfaceManager.`interface`.use {
            it.requestMusicAutoComplete(query)
        }

        val items = result.contents.flatMap {
            it.searchSuggestionsSectionRenderer.contents.mapNotNull {
                if (it.searchSuggestionRenderer != null) {
                    SearchText(it.searchSuggestionRenderer.suggestion.joinRuns())
                } else if (it.musicResponsiveListItemRenderer != null) {
                    val item = it.musicResponsiveListItemRenderer
                    val thumbnail = item.thumbnail.musicThumbnailRenderer
                        .thumbnail.thumbnails.first().url
                    val url = item.navigationEndpoint.toUrl()
                    val artist = item.flexColumns.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text?.joinRuns()
                    if (item.navigationEndpoint.watchEndpoint != null) {
                        SearchTrack(
                            item.flexColumns.first().musicResponsiveListItemFlexColumnRenderer.text.joinRuns(),
                            artist,
                            -1,
                            item.navigationEndpoint.watchEndpoint.videoId,
                            false,
                            url,
                            thumbnail,
                            null
                        )
                    } else if (item.navigationEndpoint.browseEndpoint != null) {
                        val type =
                            item.navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType
                        val identifier = item.navigationEndpoint.browseEndpoint.browseId
                        val name = item.flexColumns.first().musicResponsiveListItemFlexColumnRenderer.text.joinRuns()
                        when (type) {
                            PageType.MUSIC_PAGE_TYPE_ALBUM -> SearchAlbum(
                                identifier,
                                name,
                                artist,
                                url,
                                thumbnail,
                                null,
                                null
                            )

                            PageType.MUSIC_PAGE_TYPE_ARTIST -> SearchArtist(
                                identifier, name, url, thumbnail
                            )
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

        }
        return SearchResult(
            items.filterIfEnabled<SearchAlbum>("album" in types),
            items.filterIfEnabled<SearchArtist>("artists" in types),
            emptyList(),
            items.filterIfEnabled<SearchText>("texts" in types),
            items.filterIfEnabled<SearchTrack>("tracks" in types),
        )
    }

    override fun shutdown() = httpInterfaceManager.close()
}

private inline fun <reified T : Any> List<Any>.filterIfEnabled(enabled: Boolean) =
    if (enabled) filterIsInstance<T>() else emptyList()
