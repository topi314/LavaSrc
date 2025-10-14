package com.github.topi314.lavasrc.protocol

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val spotify: SpotifyConfig? = null,
    val appleMusic: AppleMusicConfig? = null,
    val deezer: DeezerConfig? = null,
    val yandexMusic: YandexMusicConfig? = null,
    val vkMusic: VkMusicConfig? = null,
    val qobuz: QobuzConfig? = null,
    val ytdlp: YtdlpConfig? = null,
    val pandora: PandoraConfig? = null,
)

@Serializable
data class SpotifyConfig(
    val clientId: String? = null,
    val clientSecret: String? = null,
    val spDc: String? = null,
    val preferAnonymousToken: Boolean? = null,
    val customTokenEndpoint: String? = null,
)

@Serializable
data class AppleMusicConfig(
    val mediaAPIToken: String? = null,
)

@Serializable
data class DeezerConfig(
    val arl: String? = null,
    val formats: List<DeezerTrackFormat>? = null,
)

@Suppress("unused")
@Serializable
enum class DeezerTrackFormat {
    FLAC,
    MP3_320,
    MP3_256,
    MP3_128,
    MP3_64,
    AAC_64
}

@Serializable
data class YandexMusicConfig(
    val accessToken: String? = null,
)

@Serializable
data class VkMusicConfig(
    val userToken: String? = null,
)

@Serializable
data class QobuzConfig(
    val userOauthToken: String? = null,
    val appId: String? = null,
    val appSecret: String? = null,
)

@Serializable
data class YtdlpConfig(
    val path: String? = null,
    val searchLimit: Int? = null,
    val customLoadArgs: List<String>? = null,
    val customPlaybackArgs: List<String>? = null,
)

@Serializable
data class PandoraConfig(
    val cookie: String? = null,
    val csrfToken: String? = null,
    val authToken: String? = null,
)