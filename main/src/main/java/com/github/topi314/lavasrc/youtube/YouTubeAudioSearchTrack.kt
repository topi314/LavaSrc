package com.github.topi314.lavasrc.youtube

import com.github.topi314.lavasrc.ExtendedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor

class YouTubeAudioSearchTrack(trackInfo: AudioTrackInfo, albumName: String?) :
    ExtendedAudioTrack(trackInfo, albumName, null, null, null, null, false) {
    override fun process(executor: LocalAudioTrackExecutor?) = Unit
}
