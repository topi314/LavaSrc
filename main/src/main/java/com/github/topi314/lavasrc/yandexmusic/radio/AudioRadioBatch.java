package com.github.topi314.lavasrc.yandexmusic.radio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class AudioRadioBatch {

    private final String batchId;
    private final List<AudioTrack> audioTracks;

    public AudioRadioBatch(String batchId, List<AudioTrack> audioTracks) {
        this.batchId = batchId;
        this.audioTracks = audioTracks;
    }

    public String getBatchId() {
        return batchId;
    }

    public List<AudioTrack> getAudioTracks() {
        return audioTracks;
    }
}
