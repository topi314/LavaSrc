package com.github.topi314.lavasrc.protocol

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField


@Serializable
data class SearchResult(
    val albums: List<SearchAlbum>,
    val artist: List<SearchArtist>,
    val playlist: List<SearchPlaylist>,
    val tracks: List<SearchTrack>,
    val texts: List<SearchText>
) {
    companion object {
        @JvmField
        val EMPTY = SearchResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

@Serializable
data class SearchAlbum(
    val identifier: String,
    val name: String,
    val artist: String,
    val url: String,
    val trackCount: Int,
    val artworkUrl: String?,
    val isrc: String?
)


@Serializable
data class SearchArtist(
    val identifier: String,
    val name: String,
    val url: String,
    val artworkUrl: String?,
)

@Serializable
data class SearchPlaylist(
    val identifier: String,
    val name: String,
    val url: String,
    val artworkUrl: String?,
    val trackCount: Int
)


@Serializable
data class SearchText(val text: String)

@Serializable
data class SearchTrack(
    val title: String,
    val author: String,
    val length: Long,
    val identifier: String,
    val isStream: Boolean,
    val uri: String?,
    val artworkUrl: String?,
    val isrc: String?
)
