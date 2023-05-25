package com.github.topisenpai.lavasrc.mirror;

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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public abstract class MirroringAudioTrack extends DelegatedAudioTrack {

	private static final Logger log = LoggerFactory.getLogger(MirroringAudioTrack.class);

	protected final MirroringAudioSourceManager sourceManager;
	private AudioTrack delegate;

	public MirroringAudioTrack(AudioTrackInfo trackInfo, MirroringAudioSourceManager sourceManager) {
		super(trackInfo);
		this.sourceManager = sourceManager;
	}

	@Nullable
	public AudioTrack getDelegate() {
		return this.delegate;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		AudioItem track = this.sourceManager.getResolver().apply(this);

		if (track instanceof AudioPlaylist) {
			track = ((AudioPlaylist) track).getTracks().get(0);
		}
		if (track instanceof InternalAudioTrack) {
			this.delegate = (AudioTrack) track;
			processDelegate((InternalAudioTrack) track, executor);
			return;
		}
		throw new FriendlyException("No matching track found", FriendlyException.Severity.COMMON, new TrackNotFoundException());
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

	public AudioItem loadItem(String query) {
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
