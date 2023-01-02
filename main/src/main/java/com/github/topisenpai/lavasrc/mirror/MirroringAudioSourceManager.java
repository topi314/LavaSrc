package com.github.topisenpai.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.DataOutput;
import java.io.IOException;

public abstract class MirroringAudioSourceManager implements AudioSourceManager {

    public static final String ISRC_PATTERN = "%ISRC%";
    public static final String QUERY_PATTERN = "%QUERY%";
    protected final AudioPlayerManager audioPlayerManager;
    protected final MirroringAudioTrackLookup mirroringAudioTrackLookup;
    protected String[] providers = {
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytsearch:" + QUERY_PATTERN
    };

    protected MirroringAudioSourceManager(String[] providers, AudioPlayerManager audioPlayerManager) {
        this(providers, audioPlayerManager, null);
    }

    protected MirroringAudioSourceManager(String[] providers, AudioPlayerManager audioPlayerManager, MirroringAudioTrackLookup mirroringAudioTrackLookup) {
        if (providers != null && providers.length > 0) {
            this.providers = providers;
        }
        this.audioPlayerManager = audioPlayerManager;
        this.mirroringAudioTrackLookup = mirroringAudioTrackLookup;
    }

    public String[] getProviders() {
        return this.providers;
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager;
    }

    public MirroringAudioTrackLookup getDelegatedAudioLookup() {
        return this.mirroringAudioTrackLookup;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        var isrcAudioTrack = ((MirroringAudioTrack) track);
        DataFormatTools.writeNullableText(output, isrcAudioTrack.getISRC());
        DataFormatTools.writeNullableText(output, isrcAudioTrack.getArtworkURL());
    }

}
