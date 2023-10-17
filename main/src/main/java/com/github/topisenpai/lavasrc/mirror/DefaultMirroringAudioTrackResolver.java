package com.github.topisenpai.lavasrc.mirror;

import com.github.topisenpai.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topisenpai.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager.ISRC_PATTERN;
import static com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager.QUERY_PATTERN;

public class DefaultMirroringAudioTrackResolver implements MirroringAudioTrackResolver {

	private static final Logger log = LoggerFactory.getLogger(DefaultMirroringAudioTrackResolver.class);

	private String[] providers = {
			"ytsearch:\"" + ISRC_PATTERN + "\"",
			"ytsearch:" + QUERY_PATTERN
	};

	public DefaultMirroringAudioTrackResolver(String[] providers) {
		if (providers != null && providers.length > 0) {
			this.providers = providers;
		}
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
				if (mirroringAudioTrack.getISRC() != null) {
					provider = provider.replace(ISRC_PATTERN, mirroringAudioTrack.getISRC());
				} else {
					log.debug("Ignoring identifier \"{}\" because this track does not have an ISRC!", provider);
					continue;
				}
			}

			provider = provider.replace(QUERY_PATTERN, getTrackTitle(mirroringAudioTrack));
			try {
				track = mirroringAudioTrack.loadItem(provider);
			}
			catch (Exception e) {
				log.error("Failed to load track from provider \"{}\"!", provider, e);
			}
			if (track != AudioReference.NO_TRACK) {
				break;
			}
		}

		return track;
	}

	public String getTrackTitle(MirroringAudioTrack mirroringAudioTrack) {
		var query = mirroringAudioTrack.getInfo().title;
		if (!mirroringAudioTrack.getInfo().author.equals("unknown")) {
			query += " " + mirroringAudioTrack.getInfo().author;
		}
		return query;
	}

}
