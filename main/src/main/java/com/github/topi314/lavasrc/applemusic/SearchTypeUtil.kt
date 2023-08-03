@file:JvmName("SearchTypeUtil")

package com.github.topi314.lavasrc.applemusic

import com.github.topi314.lavasearch.protocol.SearchType

@JvmName("buildAppleMusicTypes")
fun Collection<SearchType>.toAppleMusicTypes(): String {
    return asSequence()
        .mapNotNull(SearchType::appleMusicName)
        .joinToString(",")
}

private val SearchType.appleMusicName: String?
    get() = when (this) {
        SearchType.TRACK -> "songs"
        SearchType.ALBUM -> "albums"
        SearchType.ARTIST -> "artists"
        SearchType.PLAYLIST -> "playlists"
        SearchType.TEXT -> null
    }
