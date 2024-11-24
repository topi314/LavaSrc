package com.github.topi314.lavasrc.youtube.innertube

import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestionsSectionRendererContent(
    val searchSuggestionsSectionRenderer: InnerTubeBox<SearchSuggestionsRendererContent>,
)

@Serializable
data class SearchSuggestionsRendererContent(
    val searchSuggestionRenderer: SearchSuggestionsRenderer? = null,
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
)

@Serializable
data class SearchSuggestionsRenderer(val suggestion: Suggestion) {
    @Serializable
    data class Suggestion(val runs: List<Run> = emptyList()) {
        @Serializable
        data class Run(val text: String, val bold: Boolean = false)

        fun joinRuns() = runs.joinToString("", transform = Run::text)
    }
}

@Serializable
data class MusicResponsiveListItemRenderer(
    val navigationEndpoint: NavigationEndpoint,
    val thumbnail: Thumbnail,
    val flexColumns: List<FlexColumn>,
) {
    @Serializable
    data class NavigationEndpoint(
        val browseEndpoint: BrowseEndpoint? = null,
        val watchEndpoint: WatchEndpoint? = null,
    ) {
        @Serializable
        data class BrowseEndpoint(val browseId: String, val browseEndpointContextSupportedConfigs: Configs) {
            @Serializable
            data class Configs(val browseEndpointContextMusicConfig: Config) {
                @Serializable
                data class Config(val pageType: Type) {
                    @Serializable
                    enum class Type {
                        MUSIC_PAGE_TYPE_ALBUM,
                        MUSIC_PAGE_TYPE_ARTIST,
                        MUSIC_PAGE_TYPE_PLAYLIST
                    }
                }
            }
        }

        @Serializable
        data class WatchEndpoint(val videoId: String)
    }

    @Serializable
    data class Thumbnail(val musicThumbnailRenderer: MusicThumbnailRenderer) {
        @Serializable
        data class MusicThumbnailRenderer(val thumbnail: ThumbnailList) {
            @Serializable
            data class ThumbnailList(val thumbnails: List<Entry>) {
                @Serializable
                data class Entry(val url: String)
            }
        }
    }

    @Serializable
    data class FlexColumn(val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(val text: SearchSuggestionsRenderer.Suggestion)
    }
}
