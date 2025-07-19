package com.github.topi314.lavasrc.amazonmusic;

import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry;
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;

import java.io.DataOutput;
import java.io.DataInput;
import java.io.IOException;

public class AmazonMusicAudioTrack extends DelegatedAudioTrack {
    private final String audioUrl;
    private final String isrc;
    private final AmazonMusicSourceManager sourceManager;
    private final HttpAudioSourceManager httpSourceManager;
    private final String artworkUrl;

    public AmazonMusicAudioTrack(AudioTrackInfo trackInfo, String audioUrl, String isrc, String artworkUrl, AmazonMusicSourceManager sourceManager) {
        super(trackInfo);
        this.audioUrl = audioUrl;
        this.isrc = isrc;
        this.artworkUrl = artworkUrl;
        this.sourceManager = sourceManager;
        this.httpSourceManager = new HttpAudioSourceManager();
    }

    public String getIsrc() {
        return isrc;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (audioUrl == null || audioUrl.isEmpty()) {
            System.err.println("[AmazonMusicAudioTrack] [ERROR] Missing or invalid audioUrl for track: " + trackInfo.identifier);
            System.err.println("[AmazonMusicAudioTrack] [ERROR] Full trackInfo: " + trackInfo);
            throw new IllegalStateException("Missing or invalid audioUrl for Amazon Music track.");
        }

        System.out.println("[AmazonMusicAudioTrack] [INFO] Processing track with audioUrl: " + audioUrl);

        MediaContainerDescriptor descriptor = null;

        InternalAudioTrack httpTrack = new HttpAudioTrack(
                new AudioTrackInfo(
                        trackInfo.title,
                        trackInfo.author,
                        trackInfo.length,
                        trackInfo.identifier,
                        trackInfo.isStream,
                        audioUrl
                ),
                descriptor,
                httpSourceManager
        );
        processDelegate(httpTrack, executor);
    }

    public void encode(DataOutput output) throws IOException {
        output.writeUTF(audioUrl != null ? audioUrl : "");
    }

    public static AmazonMusicAudioTrack decode(AudioTrackInfo trackInfo, DataInput input, AmazonMusicSourceManager sourceManager) throws IOException {
        String audioUrl = input.readUTF();
        // Placeholder values for ISRC and artworkUrl since they are not encoded
        String isrc = null;
        String artworkUrl = null;
        return new AmazonMusicAudioTrack(trackInfo, audioUrl, isrc, artworkUrl, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
