package com.github.topisenpai.lavasrc.mirror;

import com.github.topisenpai.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topisenpai.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager.ISRC_PATTERN;
import static com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager.QUERY_PATTERN;

public class DefaultMirroringAudioTrackLookup implements MirroringAudioTrackLookup {

    private static final Logger log = LoggerFactory.getLogger(DefaultMirroringAudioTrackLookup.class);

    public static final String[] DEFAULT_PROVIDERS = {
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytsearch:" + QUERY_PATTERN
    };

    private final String[] providers;

    public DefaultMirroringAudioTrackLookup(final String[] providers) {
        this.providers = providers != null && providers.length > 0 ? providers : DEFAULT_PROVIDERS;
    }

    @Override
    public AudioItem apply(MirroringAudioTrack mirroringAudioTrack) {

        AudioItem track = AudioReference.NO_TRACK;
        for (var provider : providers) {
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

            provider = provider.replace(QUERY_PATTERN, MirroringAudioTrackLookupUtil.getTrackTitle(mirroringAudioTrack));
            track = MirroringAudioTrackLookupUtil.loadItem(provider, mirroringAudioTrack.getMirroringSourceManager());
            if (track != AudioReference.NO_TRACK) {
                break;
            }
        }

        return track;
    }

}
