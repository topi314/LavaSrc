package com.github.topi314.lavasrc.source.vkmusic;

import com.github.topi314.lavasrc.extended.ExtendedAudioTrack;
import com.github.topi314.lavasrc.extended.ExtendedAudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class VkMusicAudioTrack extends DelegatedAudioTrack implements ExtendedAudioTrack {

	private final ExtendedAudioTrackInfo extendedTrackInfo;
	private final VkMusicSourceManager sourceManager;

	public VkMusicAudioTrack(AudioTrackInfo trackInfo, ExtendedAudioTrackInfo extendedTrackInfo, VkMusicSourceManager sourceManager) {
		super(trackInfo);
		this.extendedTrackInfo = extendedTrackInfo;
		this.sourceManager = sourceManager;
	}

	@NotNull
	@Override
	public ExtendedAudioTrackInfo getExtendedInfo() {
		return this.extendedTrackInfo;
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
		var id = this.trackInfo.identifier;
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
		return new VkMusicAudioTrack(this.trackInfo, this.extendedTrackInfo, this.sourceManager);
	}

	@Override
	public VkMusicSourceManager getSourceManager() {
		return this.sourceManager;
	}
}
