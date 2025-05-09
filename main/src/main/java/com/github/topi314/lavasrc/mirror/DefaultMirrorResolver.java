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
import java.util.function.Supplier;

public class DefaultMirrorResolver implements MirrorResolver {

	private static final Logger log = LoggerFactory.getLogger(DefaultMirrorResolver.class);

	private static final Set<String> MIRRORING_RESOLVERS = Set.of(
		SpotifySourceManager.SEARCH_PREFIX,
		AppleMusicSourceManager.SEARCH_PREFIX,
		TidalSourceManager.SEARCH_PREFIX
	);

	private String[] resolvers = {
		"ytsearch:\"" + MirrorResolver.ISRC_PATTERN + "\"",
		"ytsearch:" + MirrorResolver.QUERY_PATTERN
	};
	private final Supplier<AudioPlayerManager> playerManager;

	public DefaultMirrorResolver(AudioPlayerManager playerManager, String[] resolvers) {
		this(() -> playerManager, resolvers);
	}

	public DefaultMirrorResolver(Supplier<AudioPlayerManager> playerManager, String[] resolvers) {
		this.playerManager = playerManager;
		if (resolvers != null && resolvers.length > 0) {
			this.resolvers = resolvers;
		}
	}

	@Nullable
	@Override
	public AudioTrack find(MirroringAudioTrack<?> track) {
		for (var resolver : resolvers) {
			if (MIRRORING_RESOLVERS.contains(resolver)) {
				log.debug("Ignoring resolver \"{}\" because it is a mirroring source itself!", resolver);
				continue;
			}

			if (resolver.contains(MirrorResolver.ISRC_PATTERN)) {
				var isrc = track.getInfo().isrc;
				if (isrc == null || isrc.isEmpty()) {
					log.debug("Ignoring resolver \"{}\" because this track does not have an ISRC!", resolver);
					continue;
				}
				resolver = resolver.replace(MirrorResolver.ISRC_PATTERN, isrc);
			}

			resolver = resolver.replace(MirrorResolver.QUERY_PATTERN, MirrorResolver.getTrackTitle(track));

			AudioItem item;
			try {
				item = MirrorResolver.loadItem(this.playerManager.get(), resolver);
			} catch (Exception e) {
				log.error("Failed to load track from resolver \"{}\"!", resolver, e);
				continue;
			}
			if (item instanceof AudioTrack) {
				return (AudioTrack) item;
			}
			// If the track is an empty playlist, skip the resolver
			if (item instanceof AudioPlaylist) {
				var playlist = (AudioPlaylist) item;
				if (!playlist.isSearchResult()) {
					log.debug("Ignoring non-search playlist from resolver \"{}\"!", resolver);
					continue;
				}
				if (playlist.getSelectedTrack() != null) {
					return playlist.getSelectedTrack();
				}
				if (playlist.getTracks().isEmpty()) {
					log.debug("Ignoring empty playlist from resolver \"{}\"!", resolver);
					continue;
				}
				return playlist.getTracks().get(0);
			}
		}

		return null;
	}


}
