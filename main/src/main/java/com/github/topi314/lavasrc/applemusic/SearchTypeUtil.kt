@file:JvmName("SearchTypeUtil")

package com.github.topi314.lavasrc.applemusic

import com.github.topi314.lavasearch.result.AudioSearchResult


@JvmName("buildAppleMusicTypes")
fun Collection<AudioSearchResult.Type>.toAppleMusicTypes(): String {
    return asSequence()
        .mapNotNull(AudioSearchResult.Type::appleMusicName)
        .joinToString(",")
}

private val AudioSearchResult.Type.appleMusicName: String?
    get() = when (this) {
        AudioSearchResult.Type.TRACK -> "songs"
        AudioSearchResult.Type.ALBUM -> "albums"
        AudioSearchResult.Type.ARTIST -> "artists"
        AudioSearchResult.Type.PLAYLIST -> "playlists"
        AudioSearchResult.Type.TEXT -> null
    }
