package com.github.topi314.lavasrc.mirror;

import com.github.topi314.lavasrc.source.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.source.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.source.tidal.TidalSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class DefaultMirrorResolver implements MirrorResolver {

	private static final Logger log = LoggerFactory.getLogger(DefaultMirrorResolver.class);

	private static final Set<String> MIRROR_PROVIDERS = Set.of(
		SpotifySourceManager.SEARCH_PREFIX,
		AppleMusicSourceManager.SEARCH_PREFIX,
		TidalSourceManager.SEARCH_PREFIX
	);

	private String[] providers = {
		"ytsearch:\"" + MirrorResolver.ISRC_PATTERN + "\"",
		"ytsearch:" + MirrorResolver.QUERY_PATTERN
	};
	private final AudioPlayerManager playerManager;

	public DefaultMirrorResolver(AudioPlayerManager playerManager, String[] providers) {
		this.playerManager = playerManager;
		if (providers != null && providers.length > 0) {
			this.providers = providers;
		}
	}

	@Nullable
	@Override
	public AudioTrack find(MirroringAudioTrack<?> track) {
		for (var provider : providers) {
			if (MIRROR_PROVIDERS.contains(provider)) {
				log.debug("Ignoring provider \"{}\" because it is a mirroring provider itself!", provider);
				continue;
			}

			if (provider.contains(MirrorResolver.ISRC_PATTERN)) {
				var isrc = track.getInfo().isrc;
				if (isrc == null || isrc.isEmpty()) {
					log.debug("Ignoring provider \"{}\" because this track does not have an ISRC!", provider);
					continue;
				}
				provider = provider.replace(MirrorResolver.ISRC_PATTERN, isrc);
			}

			provider = provider.replace(MirrorResolver.QUERY_PATTERN, MirrorResolver.getTrackTitle(track));

			AudioItem item;
			try {
				item = MirrorResolver.loadItem(this.playerManager, provider);
			} catch (Exception e) {
				log.error("Failed to load track from provider \"{}\"!", provider, e);
				continue;
			}
			if (item instanceof AudioTrack) {
				return (AudioTrack) item;
			}
			// If the track is an empty playlist, skip the provider
			if (item instanceof AudioPlaylist) {
				var playlist = (AudioPlaylist) item;
				if (!playlist.isSearchResult()) {
					log.debug("Ignoring non-search playlist from provider \"{}\"!", provider);
					continue;
				}
				if (playlist.getSelectedTrack() != null) {
					return playlist.getSelectedTrack();
				}
				if (playlist.getTracks().isEmpty()) {
					log.debug("Ignoring empty playlist from provider \"{}\"!", provider);
					continue;
				}
				return playlist.getTracks().get(0);
			}
		}

		return null;
	}


}
