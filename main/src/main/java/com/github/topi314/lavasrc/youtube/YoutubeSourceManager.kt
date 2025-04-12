package com.github.topi314.lavasrc.youtube

import com.github.topi314.lavalyrics.AudioLyricsManager
import com.github.topi314.lavalyrics.lyrics.AudioLyrics
import com.github.topi314.lavasearch.AudioSearchManager
import com.github.topi314.lavasearch.result.AudioSearchResult
import com.github.topi314.lavasearch.result.AudioText
import com.github.topi314.lavasearch.result.BasicAudioSearchResult
import com.github.topi314.lavasearch.result.BasicAudioText
import com.github.topi314.lavasrc.ExtendedAudioPlaylist
import com.github.topi314.lavasrc.youtube.innertube.MusicResponsiveListItemRenderer
import com.github.topi314.lavasrc.youtube.innertube.requestLyrics
import com.github.topi314.lavasrc.youtube.innertube.requestMusicAutoComplete
import com.github.topi314.lavasrc.youtube.innertube.takeFirstSearchResult
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import dev.lavalink.youtube.YoutubeAudioSourceManager
import dev.lavalink.youtube.track.YoutubeAudioTrack
import dev.schlaubi.lyrics.LyricsNotFoundException
import org.apache.http.client.methods.HttpGet
import java.net.URLEncoder
import java.util.*
import com.github.topi314.lavasrc.youtube.innertube.MusicResponsiveListItemRenderer.NavigationEndpoint.BrowseEndpoint.Configs.Config.Type as PageType

private val searchPattern = """\["([\w\s]+)",\s*\d+,\s*\[(?:\d+,?\s*)+]""".toRegex()

private fun MusicResponsiveListItemRenderer.NavigationEndpoint.toUrl() = when {
    browseEndpoint != null -> when (browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType) {
        PageType.MUSIC_PAGE_TYPE_PLAYLIST, PageType.MUSIC_PAGE_TYPE_ALBUM -> "https://music.youtube.com/browse/${browseEndpoint.browseId}"
        PageType.MUSIC_PAGE_TYPE_ARTIST -> "https://music.youtube.com/channel/${browseEndpoint.browseId}"
    }

    watchEndpoint != null -> "https://music.youtube.com/watch?v=${watchEndpoint.videoId}"
    else -> error("Unknown endpoint: $this")
}

class YoutubeSearchManager(
    private val playerManager: () -> AudioPlayerManager,
    private val region: String,
    private val language: String
) : AudioSearchManager, AudioLyricsManager {

    constructor(
        playerManager: () -> AudioPlayerManager,
        region: String,
    ) : this(playerManager, region, "en")

    companion object {
        const val SEARCH_PREFIX = "ytsearch:"
        const val MUSIC_SEARCH_PREFIX = "ytmsearch:"
        val SEARCH_TYPES = setOf(
            AudioSearchResult.Type.ALBUM,
            AudioSearchResult.Type.ARTIST,
            AudioSearchResult.Type.PLAYLIST,
            AudioSearchResult.Type.TRACK,
            AudioSearchResult.Type.TEXT
        )
    }

    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
    override fun getSourceName(): String = "youtube"

    override fun loadLyrics(track: AudioTrack): AudioLyrics? = try {
        httpInterfaceManager.`interface`.use {
            val videoId = when {
                track.sourceManager.sourceName == "youtube" -> track.info.identifier
                track.info.isrc != null -> it.takeFirstSearchResult(track.info.isrc, region)
                else -> it.takeFirstSearchResult("${track.info.title} - ${track.info.author}", region)
            } ?: return@use null

            it.requestLyrics(videoId)
        }
    } catch (e: LyricsNotFoundException) {
        null
    }

    override fun loadSearch(query: String, types: Set<AudioSearchResult.Type>): AudioSearchResult? {
        val result = httpInterfaceManager.`interface`.use {
            when {
                query.startsWith(MUSIC_SEARCH_PREFIX) ->
                    it.requestMusicAutoComplete(query.removePrefix(MUSIC_SEARCH_PREFIX), locale = Locale(language, region))

                query.startsWith(SEARCH_PREFIX) -> {
                    val response = requestYoutubeAutoComplete(query.removePrefix(SEARCH_PREFIX))
                    return BasicAudioSearchResult(emptyList(), emptyList(), emptyList(), emptyList(), response)
                }

                else -> return null
            }
        }

        val items = result.contents.flatMap {
            it.searchSuggestionsSectionRenderer.contents.mapNotNull { suggestionRenderer ->
                if (suggestionRenderer.searchSuggestionRenderer != null) {
                    BasicAudioText(suggestionRenderer.searchSuggestionRenderer.suggestion.joinRuns())
                } else if (suggestionRenderer.musicResponsiveListItemRenderer != null) {
                    val item = suggestionRenderer.musicResponsiveListItemRenderer
                    val thumbnail = item.thumbnail.musicThumbnailRenderer
                        .thumbnail.thumbnails.first().url
                    val url = item.navigationEndpoint.toUrl()
                    val artist = item.flexColumns.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text?.runs?.getOrNull(2)?.text ?: "Unknown Author"
                    if (item.navigationEndpoint.watchEndpoint != null) {
                        val info = AudioTrackInfo(
                            item.flexColumns.first().musicResponsiveListItemFlexColumnRenderer.text.joinRuns(),
                            artist,
                            -1L,
                            item.navigationEndpoint.watchEndpoint.videoId,
                            false,
                            url,
                            thumbnail,
                            null
                        )
                        YoutubeAudioTrack(info, playerManager().source(YoutubeAudioSourceManager::class.java))
                    } else if (item.navigationEndpoint.browseEndpoint != null) {
                        val type =
                            item.navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType
                        val name = item.flexColumns.first().musicResponsiveListItemFlexColumnRenderer.text.joinRuns()
                        when (type) {
                            PageType.MUSIC_PAGE_TYPE_ALBUM -> ExtendedAudioPlaylist(
                                name,
                                emptyList(),
                                ExtendedAudioPlaylist.Type.ALBUM,
                                url,
                                thumbnail,
                                artist,
                                null
                            )

                            PageType.MUSIC_PAGE_TYPE_ARTIST -> ExtendedAudioPlaylist(
                                "$name's Top Tracks",
                                emptyList(),
                                ExtendedAudioPlaylist.Type.ARTIST,
                                url,
                                thumbnail,
                                artist,
                                null
                            )

                            PageType.MUSIC_PAGE_TYPE_PLAYLIST -> ExtendedAudioPlaylist(
                                name,
                                emptyList(),
                                ExtendedAudioPlaylist.Type.PLAYLIST,
                                url,
                                thumbnail,
                                artist,
                                null
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
        val finalTypes = types.ifEmpty { SEARCH_TYPES }
        return BasicAudioSearchResult(
            items.filter<AudioTrack>(AudioSearchResult.Type.TRACK in finalTypes),
            items.filter(AudioSearchResult.Type.ALBUM in finalTypes, ExtendedAudioPlaylist.Type.ALBUM),
            items.filter(AudioSearchResult.Type.ARTIST in finalTypes, ExtendedAudioPlaylist.Type.ARTIST),
            items.filter(AudioSearchResult.Type.PLAYLIST in finalTypes, ExtendedAudioPlaylist.Type.PLAYLIST),
            items.filter<AudioText>(AudioSearchResult.Type.TEXT in finalTypes),
        )
    }

    private fun requestYoutubeAutoComplete(query: String): List<AudioText> {
        val input = httpInterfaceManager.`interface`.use {
            val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8)
            val request =
                HttpGet("https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&q=$encodedQuery&gl=$region&hl=$language")
            it.execute(request).entity.content.readAllBytes().decodeToString()
        }

        return searchPattern.findAll(input).map {
            val (hint) = it.destructured

            BasicAudioText(hint)
        }.toList()
    }

    override fun shutdown() = httpInterfaceManager.close()
}

private inline fun <reified T : Any> List<Any>.filter(enabled: Boolean) =
    if (enabled) filterIsInstance<T>() else emptyList()

private fun List<Any>.filter(enabled: Boolean, type: ExtendedAudioPlaylist.Type) =
    if (enabled) asSequence().filterIsInstance<ExtendedAudioPlaylist>().filter { it.type == type }
        .toList() else emptyList()
