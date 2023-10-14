package com.github.topi314.lavasrc.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtendedPlaylistInfo(
    val type: Type = Type.PLAYLIST,
    val url: String? = null,
    val artworkUrl: String? = null,
    val author: String? = null,
    val totalTracks: Int? = null,
) {
    /**
     * The type of the originating track list.
     */
    @Serializable
    enum class Type {
        /**
         * A playlist from a music service.
         */
        @SerialName("playlist")
        PLAYLIST,

        /**
         * An album listed on a music service.
         */
        @SerialName("album")
        ALBUM,

        /**
         * An auto-generated playlist about an author from a music service.
         */
        @SerialName("artist")
        ARTIST,

        /**
         * Recommendations from a music service (currently only Spotify).
         */
        @SerialName("recommendations")
        RECOMMENDATIONS
    }
}

@Serializable
data class ExtendedTrackInfo(
    val albumName: String? = null,
    val albumUrl: String? = null,
    val artistUrl: String? = null,
    val artistArtworkUrl: String? = null,
    val previewUrl: String? = null,
    val isPreview: Boolean = false
)