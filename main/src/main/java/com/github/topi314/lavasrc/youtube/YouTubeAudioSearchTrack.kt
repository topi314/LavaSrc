package com.github.topi314.lavasrc.youtube

import com.github.topi314.lavasrc.ExtendedAudioTrack
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor

class YouTubeAudioSearchTrack(
    private val sourceManager: YoutubeAudioSourceManager,
    trackInfo: AudioTrackInfo,
    albumName: String?
) :
    ExtendedAudioTrack(trackInfo, albumName, null, null, null, null, false) {
    override fun process(executor: LocalAudioTrackExecutor?) = Unit

    override fun makeClone() = YouTubeAudioSearchTrack(sourceManager, trackInfo, albumName)

    override fun getSourceManager(): AudioSourceManager {
        return sourceManager
    }
}
