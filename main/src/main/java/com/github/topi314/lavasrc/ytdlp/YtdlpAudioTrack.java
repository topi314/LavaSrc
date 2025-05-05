package com.github.topi314.lavasrc.ytdlp;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class YtdlpAudioTrack extends ExtendedAudioTrack {

	private static final Logger log = LoggerFactory.getLogger(YtdlpAudioTrack.class);

	private final YtdlpAudioSourceManager sourceManager;

	public YtdlpAudioTrack(AudioTrackInfo trackInfo, YtdlpAudioSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public YtdlpAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, YtdlpAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
		this.sourceManager = sourceManager;
	}

	private JsonBrowser getStreamUrl(String url) throws IOException {
		var args = new ArrayList<>(List.of(this.sourceManager.getCustomPlaybackArgs()));
		args.add(url);
		var process = this.sourceManager.getProcess(args);
		return this.sourceManager.getProcessJsonOutput(process);
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		var streamJson = this.getStreamUrl(trackInfo.uri);

		var streamUrl = new URI(streamJson.get("url").text());
		var format = streamJson.get("ext").text();
		var contentLength = streamJson.get("filesize").asLong(Units.CONTENT_LENGTH_UNKNOWN);
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			if (trackInfo.isStream) {
				if (format.equals("webm")) {
					throw new FriendlyException("YouTube WebM streams are currently not supported.", FriendlyException.Severity.COMMON, null);
				}
				processDelegate(new YoutubeMpegStreamAudioTrack(trackInfo, httpInterface, streamUrl), executor);
				return;
			}

			try (var stream = new YoutubePersistentHttpStream(httpInterface, streamUrl, contentLength)) {
				if (format.equals("webm")) {
					processDelegate(new MatroskaAudioTrack(this.trackInfo, stream), executor);
				} else {
					processDelegate(new MpegAudioTrack(this.trackInfo, stream), executor);
				}
			}
		}
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new YtdlpAudioTrack(this.trackInfo, this.albumName, this.albumUrl, this.artistUrl, this.artistArtworkUrl, this.previewUrl, this.isPreview, this.sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

}
