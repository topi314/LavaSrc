package com.github.topi314.lavasrc.flowerytts;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.flac.FlacAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.ogg.OggAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.wav.WavAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class FloweryTTSAudioTrack extends DelegatedAudioTrack {
	private static final Map<String, Class<? extends BaseAudioTrack>> AUDIO_FORMATS = Map.of(
		"mp3", Mp3AudioTrack.class,
		"ogg_opus", OggAudioTrack.class,
		"ogg_vorbis", OggAudioTrack.class,
		"wav", WavAudioTrack.class,
		"flac", FlacAudioTrack.class,
		"aac", AdtsAudioTrack.class
	);
	public static final String API_BASE = "https://api.flowery.pw/v1/tts";

	private final FloweryTTSSourceManager sourceManager;

	public FloweryTTSAudioTrack(AudioTrackInfo trackInfo, FloweryTTSSourceManager sourceManager) {
		super(trackInfo);
		this.sourceManager = sourceManager;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			var queryParams = new URIBuilder(this.trackInfo.identifier).getQueryParams();
			var apiUri = new URIBuilder(API_BASE);
			String format = "mp3";

			apiUri.addParameter("text", this.trackInfo.title);
			for (var entry : this.sourceManager.getDefaultConfig().entrySet()) {
				var value = queryParams.stream()
					.filter((p) -> entry.getKey().equals(p.getName()))
					.map(NameValuePair::getValue)
					.findFirst()
					.orElse(entry.getValue());
				apiUri.addParameter(entry.getKey(), value);
				if ("audio_format".equals(entry.getKey())) {
					format = value;
				}
			}
			System.out.println(apiUri.build());
			try (var stream = new PersistentHttpStream(httpInterface, apiUri.build(), null)) {
				var audioTrackClass = AUDIO_FORMATS.get(format);
				if (audioTrackClass == null) {
					throw new IllegalArgumentException("Invalid audio format");
				}
				var streamClass = ("aac".equals(format)) ? InputStream.class : SeekableInputStream.class;
				processDelegate(audioTrackClass.getConstructor(AudioTrackInfo.class, streamClass).newInstance(this.trackInfo, stream), executor);
			}
		}
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new FloweryTTSAudioTrack(this.trackInfo, this.sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

}