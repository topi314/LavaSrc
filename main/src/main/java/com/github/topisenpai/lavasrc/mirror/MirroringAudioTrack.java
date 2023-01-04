package com.github.topisenpai.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public abstract class MirroringAudioTrack extends DelegatedAudioTrack {

    protected final String isrc;
    protected final String artworkURL;
    protected final MirroringAudioSourceManager sourceManager;

    public MirroringAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, MirroringAudioSourceManager sourceManager) {
        super(trackInfo);
        this.isrc = isrc;
        this.artworkURL = artworkURL;
        this.sourceManager = sourceManager;
    }

    public String getISRC() {
        return this.isrc;
    }

    public String getArtworkURL() {
        return this.artworkURL;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        AudioItem track = this.sourceManager.getMirroringAudioTrackLookup().apply(this);

        if (track instanceof AudioPlaylist) {
            track = ((AudioPlaylist) track).getTracks().get(0);
        }
        if (track instanceof InternalAudioTrack) {
            processDelegate((InternalAudioTrack) track, executor);
            return;
        }
        throw new FriendlyException("No matching track found", FriendlyException.Severity.COMMON, new TrackNotFoundException());
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return this.sourceManager;
    }

    public MirroringAudioSourceManager getMirroringSourceManager() {
        return this.sourceManager;
    }

}
