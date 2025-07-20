package com.github.topi314.lavasrc.lastfm;

import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;

public class LastfmAudioTrack extends MirroringAudioTrack {

    public LastfmAudioTrack(AudioTrackInfo trackInfo, MirroringAudioSourceManager sourceManager) {
        super(trackInfo, null, null, null, trackInfo.artworkUrl, null, false, sourceManager);
    }

    public LastfmAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl,
                            String artistUrl, String artworkUrl, String previewUrl,
                            boolean isPreview, MirroringAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artworkUrl, previewUrl, isPreview, sourceManager);
    }

    @Override
    protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream inputStream) {
        return new LastfmAudioTrack(trackInfo, (MirroringAudioSourceManager) this.sourceManager);
    }
}
