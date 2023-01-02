package com.github.topisenpai.lavasrc.mirror;

import com.github.topisenpai.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topisenpai.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutput;
import java.io.IOException;

public abstract class MirroringAudioSourceManager implements AudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(MirroringAudioSourceManager.class);

    public static final String ISRC_PATTERN = "%ISRC%";
    public static final String QUERY_PATTERN = "%QUERY%";
    public static final String[] DEFAULT_PROVIDERS = {
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytsearch:" + QUERY_PATTERN
    };
    protected final AudioPlayerManager audioPlayerManager;
    protected final MirroringAudioTrackLookup mirroringAudioTrackLookup;

    protected MirroringAudioSourceManager(AudioPlayerManager audioPlayerManager, MirroringAudioTrackLookup mirroringAudioTrackLookup) {
        this.audioPlayerManager = audioPlayerManager;
        this.mirroringAudioTrackLookup = mirroringAudioTrackLookup;
    }

    protected static MirroringAudioTrackLookup defaultMirroringAudioTrackLookup(final String[] providers) {

        return mirroringAudioTrack -> {

            String[] effectiveProviders = providers != null && providers.length > 0 ? providers : DEFAULT_PROVIDERS;

            AudioItem track = AudioReference.NO_TRACK;
            for (var provider : effectiveProviders) {
                if (provider.startsWith(SpotifySourceManager.SEARCH_PREFIX)) {
                    log.warn("Can not use spotify search as search provider!");
                    continue;
                }

                if (provider.startsWith(AppleMusicSourceManager.SEARCH_PREFIX)) {
                    log.warn("Can not use apple music search as search provider!");
                    continue;
                }

                if (provider.contains(ISRC_PATTERN)) {
                    if (mirroringAudioTrack.isrc != null) {
                        provider = provider.replace(ISRC_PATTERN, mirroringAudioTrack.isrc);
                    } else {
                        log.debug("Ignoring identifier \"" + provider + "\" because this track does not have an ISRC!");
                        continue;
                    }
                }

                provider = provider.replace(QUERY_PATTERN, mirroringAudioTrack.getTrackTitle());
                track = MirroringAudioTrack.loadItem(provider, mirroringAudioTrack.getMirroringSourceManager());
                if (track != AudioReference.NO_TRACK) {
                    break;
                }
            }

            return track;
        };
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
