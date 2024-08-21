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
		var token = sourceManager.getUserToken();
		var id = this.trackInfo.identifier;

		if (token == null || token.isEmpty()) {
			throw new IllegalArgumentException("Vk Music user token must be set");
		}

		var json = this.sourceManager.getJson("audio.getById", "&audios=" + id)
			.get("response");
		if (
			json.isNull()
				|| json.values().isEmpty()
				|| json.values().get(0).get("url").isNull()
		) {
			throw new IllegalStateException("No download url found for track " + id);
		}

		return new URI(json.values().get(0).get("url").text());
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
