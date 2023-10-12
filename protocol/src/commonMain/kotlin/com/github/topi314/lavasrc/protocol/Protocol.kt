package com.github.topi314.lavasrc.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtendedPlaylistInfo(
    val type: Type,
    val url: String,
    val artworkUrl: String,
    val author: String
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
data class ExtendedAudioTrack(
    val albumName: String?,
    val albumUrl: String?,
    val artistUrl: String?,
    val artistArtworkUrl: String?,
    val previewUrl: String?,
    val isPreview: Boolean
)