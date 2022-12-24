package com.github.topisenpai.lavasrc.applemusic;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class AppleMusicAudioTrack extends MirroringAudioTrack {

    public AppleMusicAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, AppleMusicSourceManager sourceManager) {
        super(trackInfo, isrc, artworkURL, sourceManager);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new AppleMusicAudioTrack(trackInfo, isrc, artworkURL, (AppleMusicSourceManager) sourceManager);
    }

}
