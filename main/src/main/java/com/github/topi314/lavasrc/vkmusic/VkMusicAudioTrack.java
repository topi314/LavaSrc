package com.github.topi314.lavasrc.vkmusic;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class VkMusicAudioTrack extends ExtendedAudioTrack {

	private final VkMusicSourceManager sourceManager;

	public VkMusicAudioTrack(AudioTrackInfo trackInfo, VkMusicSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, sourceManager);
	}

	public VkMusicAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, VkMusicSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, false);
		this.sourceManager = sourceManager;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			try (var stream = new PersistentHttpStream(httpInterface, getMp3TrackUri(), this.trackInfo.length)) {
				processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
			}
		}
	}

	public URI getMp3TrackUri() throws URISyntaxException, IOException {
		String id = trackInfo.identifier;

		var response = sourceManager
			.getJson("audio.getById", "&audios=" + id)
			.get("response");

		if (response == null || response.isNull() || response.values().isEmpty()) {
			throw new IllegalStateException("Empty response for track " + id);
		}

		var url = response.values().get(0).get("url");

		if (url == null || url.text().isEmpty()) {
			throw new IllegalStateException("No download url found for track " + id);
		}

		return new URI(url.text());
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new VkMusicAudioTrack(this.trackInfo, this.sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}
}
