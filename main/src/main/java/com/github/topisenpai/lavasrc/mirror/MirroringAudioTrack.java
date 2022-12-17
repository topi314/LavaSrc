package com.github.topisenpai.lavasrc.mirror;

import com.github.topisenpai.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topisenpai.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager.ISRC_PATTERN;
import static com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager.QUERY_PATTERN;

public abstract class MirroringAudioTrack extends DelegatedAudioTrack {

	private static final Logger log = LoggerFactory.getLogger(MirroringAudioTrack.class);

	protected final MirroringAudioSourceManager sourceManager;

	public MirroringAudioTrack(AudioTrackInfo trackInfo, MirroringAudioSourceManager sourceManager) {
		super(trackInfo);
		this.sourceManager = sourceManager;
	}

	private String getTrackTitle() {
		var query = this.trackInfo.title;
		if (!this.trackInfo.author.equals("unknown")) {
			query += " " + this.trackInfo.author;
		}
		return query;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		AudioItem track = null;

		for (var provider : this.sourceManager.getProviders()) {
			if (provider.startsWith(SpotifySourceManager.SEARCH_PREFIX)) {
				log.warn("Can not use spotify search as search provider!");
				continue;
			}

			if (provider.startsWith(AppleMusicSourceManager.SEARCH_PREFIX)) {
				log.warn("Can not use apple music search as search provider!");
				continue;
			}

			if (provider.contains(ISRC_PATTERN)) {
				if (this.trackInfo.isrc != null) {
					provider = provider.replace(ISRC_PATTERN, this.trackInfo.isrc);
				} else {
					log.debug("Ignoring identifier \"" + provider + "\" because this track does not have an ISRC!");
					continue;
				}
			}

			provider = provider.replace(QUERY_PATTERN, getTrackTitle());
			track = loadItem(provider);
			if (track != AudioReference.NO_TRACK) {
				break;
			}
		}

		if (track instanceof AudioPlaylist) {
			track = ((AudioPlaylist) track).getTracks().get(0);
		}
		if (track instanceof InternalAudioTrack) {
			processDelegate((InternalAudioTrack) track, executor);
			return;
		}
		throw new FriendlyException("No matching track found", FriendlyException.Severity.COMMON, new TrackNotFoundException());
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

	private AudioItem loadItem(String query) {
		var cf = new CompletableFuture<AudioItem>();
		this.sourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler() {

			@Override
			public void trackLoaded(AudioTrack track) {
				log.debug("Track loaded: " + track.getIdentifier());
				cf.complete(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				log.debug("Playlist loaded: " + playlist.getName());
				cf.complete(playlist);
			}

			@Override
			public void noMatches() {
				log.debug("No matches found for: " + query);
				cf.complete(AudioReference.NO_TRACK);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				log.debug("Failed to load: " + query);
				cf.completeExceptionally(exception);
			}
		});
		return cf.join();
	}

}
