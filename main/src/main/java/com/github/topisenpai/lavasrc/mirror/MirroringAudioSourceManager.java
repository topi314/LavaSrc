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
    protected final DelegatedAudioLookup delegatedAudioLookup;
    protected String[] providers = {
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytsearch:" + QUERY_PATTERN
    };

    protected MirroringAudioSourceManager(String[] providers, AudioPlayerManager audioPlayerManager) {
        this(providers, audioPlayerManager, null);
    }

    protected MirroringAudioSourceManager(String[] providers, AudioPlayerManager audioPlayerManager, DelegatedAudioLookup delegatedAudioLookup) {
        if (providers != null && providers.length > 0) {
            this.providers = providers;
        }
        this.audioPlayerManager = audioPlayerManager;
        this.delegatedAudioLookup = delegatedAudioLookup;
    }

    public String[] getProviders() {
        return this.providers;
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager;
    }

    public DelegatedAudioLookup getDelegatedAudioLookup() {
        return this.delegatedAudioLookup;
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
