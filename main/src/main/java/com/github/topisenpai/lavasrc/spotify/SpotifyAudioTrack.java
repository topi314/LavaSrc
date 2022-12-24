package com.github.topisenpai.lavasrc.spotify;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class SpotifyAudioTrack extends MirroringAudioTrack {

    public SpotifyAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, SpotifySourceManager sourceManager) {
        super(trackInfo, isrc, artworkURL, sourceManager);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new SpotifyAudioTrack(trackInfo, isrc, artworkURL, (SpotifySourceManager) sourceManager);
    }

}
