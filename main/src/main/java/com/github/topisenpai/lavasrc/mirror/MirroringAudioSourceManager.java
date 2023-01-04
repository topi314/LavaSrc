package com.github.topisenpai.lavasrc.mirror;

import com.github.topisenpai.lavasrc.mirror.lookup.MirroringAudioTrackLookup;
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

    protected MirroringAudioSourceManager(AudioPlayerManager audioPlayerManager, MirroringAudioTrackLookup mirroringAudioTrackLookup) {
        this.audioPlayerManager = audioPlayerManager;
        this.mirroringAudioTrackLookup = mirroringAudioTrackLookup;
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager;
    }

    public MirroringAudioTrackLookup getMirroringAudioTrackLookup() {
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
