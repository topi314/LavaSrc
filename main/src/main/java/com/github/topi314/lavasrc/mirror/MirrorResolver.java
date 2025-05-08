package com.github.topi314.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public interface MirrorResolver {

	Logger log = LoggerFactory.getLogger(MirrorResolver.class);

	String ISRC_PATTERN = "%ISRC%";
	String QUERY_PATTERN = "%QUERY%";

	@Nullable
	AudioTrack find(MirroringAudioTrack<?> track);

	static AudioItem loadItem(AudioPlayerManager manager, String query) {
		var cf = new CompletableFuture<AudioItem>();
		manager.loadItem(query, new AudioLoadResultHandler() {

			@Override
			public void trackLoaded(AudioTrack track) {
				log.debug("Mirror track loaded: {}", track.getIdentifier());
				cf.complete(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				log.debug("Mirror playlist loaded: {}", playlist.getName());
				cf.complete(playlist);
			}

			@Override
			public void noMatches() {
				log.debug("Mirror no matches found for: {}", query);
				cf.complete(AudioReference.NO_TRACK);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				log.debug("Mirror failed to load: {}", query);
				cf.completeExceptionally(exception);
			}
		});
		return cf.join();
	}

	static String getTrackTitle(MirroringAudioTrack<?> mirroringAudioTrack) {
		var query = mirroringAudioTrack.getInfo().title;
		if (!mirroringAudioTrack.getInfo().author.equals("unknown")) {
			query += " " + mirroringAudioTrack.getInfo().author;
		}
		return query;
	}
}
