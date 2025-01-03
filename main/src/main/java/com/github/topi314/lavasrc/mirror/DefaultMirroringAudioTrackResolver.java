package com.github.topi314.lavasrc.mirror;

import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMirroringAudioTrackResolver implements MirroringAudioTrackResolver {

	private StringCompareMirroringAudioTrackResolver advancedResolver;

	private static final Logger log = LoggerFactory.getLogger(DefaultMirroringAudioTrackResolver.class);

	private String[] providers = {
		"ytsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"",
		"ytsearch:" + MirroringAudioSourceManager.QUERY_PATTERN
	};

	public DefaultMirroringAudioTrackResolver(String[] providers) {
		if (providers != null && providers.length > 0) {
			this.providers = providers;
		}
	}

	@Override
	public AudioItem apply(MirroringAudioTrack mirroringAudioTrack) {
		for (var provider : providers) {

			if (!canBeAsearchProvider(provider)) continue;

			if (provider.contains(MirroringAudioSourceManager.ISRC_PATTERN)) {
				String isrc = mirroringAudioTrack.getInfo().isrc;
				if (isrc == null || isrc.isEmpty()) {
					log.debug("Ignoring identifier \"{}\" because this track does not have an ISRC!", provider);
					continue;
				}
				provider = provider.replace("%ISRC%", isrc);
			}

			provider = provider.replace(MirroringAudioSourceManager.QUERY_PATTERN, getTrackTitle(mirroringAudioTrack));

			AudioItem item;
			try {
				item = mirroringAudioTrack.loadItem(provider);
			} catch (Exception e) {
				log.error("Failed to load track from provider \"{}\"!", provider, e);
				continue;
			}

			if (isValidAudioItem(item)) {
				if (this.advancedResolver != null) {
					item = this.advancedResolver.apply(item, mirroringAudioTrack, provider);
					if (isValidAudioItem(item)) {
						return item;
					}
				} else {
					return item;
				}
			}
		}
		return AudioReference.NO_TRACK;
	}

	private boolean canBeAsearchProvider(String provider) {
		if (provider.startsWith(SpotifySourceManager.SEARCH_PREFIX)) {
			log.warn("Can not use spotify search as search provider!");
			return false;
		}

		if (provider.startsWith(AppleMusicSourceManager.SEARCH_PREFIX)) {
			log.warn("Can not use apple music search as search provider!");
			return false;
		}

		return true;
	}

	public String getTrackTitle(MirroringAudioTrack mirroringAudioTrack) {
		var query = mirroringAudioTrack.getInfo().title;
		if (!mirroringAudioTrack.getInfo().author.equals("unknown")) {
			if (advancedResolver != null) {
				query += " by " + mirroringAudioTrack.getInfo().author;
			} else {
				query += " " + mirroringAudioTrack.getInfo().author;
			}
		}
		return query;
	}

	public void setStringComparisonResolver(StringCompareMirroringAudioTrackResolver resolver) {
		this.advancedResolver = resolver;
		log.info("Set string comparison resolver to " + resolver.getClass().getSimpleName());
	}
	private boolean isValidAudioItem(AudioItem item) {
		boolean isPlaylistWithTracks = (item instanceof AudioPlaylist) && !((AudioPlaylist) item).getTracks().isEmpty();
		return isPlaylistWithTracks || (item != AudioReference.NO_TRACK);
	}

}
