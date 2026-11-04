package com.github.topi314.lavasrc.mirror;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.channels.ClosedByInterruptException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public abstract class MirroringAudioTrack extends ExtendedAudioTrack {

	private static final Logger log = LoggerFactory.getLogger(MirroringAudioTrack.class);

	protected final MirroringAudioSourceManager sourceManager;
	private final Set<String> failedMirrors = new HashSet<>();

	public MirroringAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
		this.sourceManager = sourceManager;
	}

	protected abstract InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream inputStream);

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		if (this.isPreview) {
			if (this.previewUrl == null) {
				throw new FriendlyException("No preview url found", FriendlyException.Severity.COMMON, new IllegalArgumentException());
			}
			try (var httpInterface = this.sourceManager.getHttpInterface()) {
				try (var stream = new PersistentHttpStream(httpInterface, new URI(this.previewUrl), this.trackInfo.length)) {
					processDelegate(createAudioTrack(this.trackInfo, stream), executor);
				}
			}
			return;
		}
		Exception playbackException = null;
		// ends once the resolver has no mirror left that didn't fail already
		while (true) {
			var track = this.sourceManager.getResolver().apply(this);

			if (track instanceof AudioPlaylist) {
				var tracks = ((AudioPlaylist) track).getTracks();
				if (tracks.isEmpty()) {
					if (playbackException != null) {
						throw playbackException;
					}
					throw new TrackNotFoundException("No tracks found in playlist or search result for track");
				}
				track = tracks.get(0);
			}
			if (!(track instanceof InternalAudioTrack)) {
				if (playbackException != null) {
					throw playbackException;
				}
				throw new TrackNotFoundException("No mirror found for track");
			}

			var internalTrack = (InternalAudioTrack) track;
			// the resolver returned a mirror that already failed, so there is nothing else to try
			if (this.hasMirrorFailed(internalTrack)) {
				throw playbackException;
			}
			internalTrack.setUserData(this.getUserData());
			log.debug("Loaded track mirror from {} {}({}) ", internalTrack.getSourceManager().getSourceName(), internalTrack.getInfo().title, internalTrack.getInfo().uri);

			var position = executor.getPosition();
			try {
				processDelegate(internalTrack, executor);
				return;
			} catch (Exception e) {
				// only try the next provider when nothing was played yet and the track wasn't stopped
				if (executor.getPosition() != position || isInterruption(e)) {
					throw e;
				}
				log.warn("Failed to play track mirror from {} {}({}), trying the next provider", internalTrack.getSourceManager().getSourceName(), internalTrack.getInfo().title, internalTrack.getInfo().uri, e);
				executor.getAudioBuffer().clear();
				this.failedMirrors.add(mirrorKey(internalTrack));
				playbackException = e;
			}
		}
	}

	/**
	 * @return whether this track already failed to play the given mirror, resolvers should skip it
	 */
	public boolean hasMirrorFailed(AudioTrack track) {
		return this.failedMirrors.contains(mirrorKey(track));
	}

	private static String mirrorKey(AudioTrack track) {
		var sourceManager = track.getSourceManager();
		return (sourceManager == null ? "" : sourceManager.getSourceName()) + ":" + track.getIdentifier();
	}

	private static boolean isInterruption(Throwable throwable) {
		if (Thread.currentThread().isInterrupted()) {
			return true;
		}
		for (var cause = throwable; cause != null; cause = cause.getCause()) {
			if (cause instanceof InterruptedException || cause instanceof ClosedByInterruptException) {
				return true;
			}
			if (cause instanceof InterruptedIOException && !(cause instanceof SocketTimeoutException)) {
				return true;
			}
		}
		return false;
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
				log.debug("Track loaded: {}", track.getIdentifier());
				cf.complete(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				log.debug("Playlist loaded: {}", playlist.getName());
				cf.complete(playlist);
			}

			@Override
			public void noMatches() {
				log.debug("No matches found for: {}", query);
				cf.complete(AudioReference.NO_TRACK);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				log.debug("Failed to load: {}", query);
				cf.completeExceptionally(exception);
			}
		});
		return cf.join();
	}

}
