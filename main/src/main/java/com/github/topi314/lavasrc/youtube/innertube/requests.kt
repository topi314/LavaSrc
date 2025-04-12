package com.github.topi314.lavasrc.youtube.innertube

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.*

val youtubeMusic = URI("https://music.youtube.com")

fun musicContext(locale: Locale): InnerTubeContext {
    val safeLocale = locale.convertToISO()
    return InnerTubeContext(
        InnerTubeContext.Client(
            "WEB_REMIX",
            "1.20230102.01.00",
            safeLocale.language,
            safeLocale.country
        )
    )
}

private fun Locale.convertToISO(): Locale = when {
    !country.isNullOrBlank() -> this
    language == "cs" -> Locale(language, "CZ")
    language == "da" -> Locale(language, "DK")
    language == "el" -> Locale(language, "GR")
    language == "hi" -> Locale(language, "IN")
    language == "ja" -> Locale(language, "JP")
    language == "uk" -> Locale(language, "UA")
    language == "vi" -> Locale(language, "VN")
    else -> Locale(language, language.uppercase(Locale.ENGLISH))
}

@Serializable
data class InnerTubeContext(val client: Client) {
    @Serializable
    data class Client(val clientName: String, val clientVersion: String, val hl: String = "en", val gl: String = "US")
}

@Serializable
data class MusicSearchRequest(override val context: InnerTubeContext, val input: String) : InnerTubeRequest

interface InnerTubeRequest {
    val context: InnerTubeContext
}
