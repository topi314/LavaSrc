package com.github.topi314.lavasrc.source.flowerytts;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.flac.FlacAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.ogg.OggAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.wav.WavAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class FloweryTTSAudioTrack extends DelegatedAudioTrack {
	private static final Logger log = LoggerFactory.getLogger(FloweryTTSAudioTrack.class);

	public static final String API_BASE = "https://api.flowery.pw/v1/tts";

	private final FloweryTTSSourceManager sourceManager;

	public FloweryTTSAudioTrack(AudioTrackInfo trackInfo, FloweryTTSSourceManager sourceManager) {
		super(trackInfo);
		this.sourceManager = sourceManager;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			var queryParams = new URIBuilder(this.trackInfo.identifier).getQueryParams()
				.stream()
				.collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));

			var apiUri = new URIBuilder(API_BASE)
				.addParameter("text", this.trackInfo.title);

			Map<String, String> config = this.sourceManager.getDefaultConfig();
			String audioFormat = queryParams.getOrDefault("audio_format", config.get("audio_format"));

			for (var entry : config.entrySet()) {
				var value = queryParams.getOrDefault(entry.getKey(), entry.getValue());
				if (value == null) {
					continue;
				}
				apiUri.addParameter(entry.getKey(), value);
			}

			URI url = apiUri.build();
			AudioFormat format = AudioFormat.getByName(audioFormat);
			log.debug("Requesting TTS URL \"{}\"", url);

			try (var stream = new PersistentHttpStream(httpInterface, url, Units.CONTENT_LENGTH_UNKNOWN)) {
				InternalAudioTrack track = format.trackFactory.apply(this.trackInfo, stream);
				processDelegate(track, executor);
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

	private enum AudioFormat {
		MP3("mp3", Mp3AudioTrack::new),
		OGG_OPUS("ogg_opus", OggAudioTrack::new),
		OGG_VORBIS("ogg_vorbis", OggAudioTrack::new),
		WAV("wav", WavAudioTrack::new),
		FLAC("flac", FlacAudioTrack::new),
		AAC("aac", AdtsAudioTrack::new);

		private final String name;
		private final BiFunction<AudioTrackInfo, PersistentHttpStream, InternalAudioTrack> trackFactory;

		AudioFormat(String name, BiFunction<AudioTrackInfo, PersistentHttpStream, InternalAudioTrack> trackFactory) {
			this.name = name;
			this.trackFactory = trackFactory;
		}

		static AudioFormat getByName(String name) {
			return Arrays.stream(values())
				.filter(e -> e.name.equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Invalid audio format"));
		}
	}
}
