package com.github.topi314.lavasrc.ytdlp;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.InputStream;

public class YTDLPAudioTrack extends ExtendedAudioTrack {

	private static final Logger log = LoggerFactory.getLogger(YTDLPAudioTrack.class);

	private final YTDLPAudioSourceManager sourceManager;

	public YTDLPAudioTrack(AudioTrackInfo trackInfo, YTDLPAudioSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public YTDLPAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, YTDLPAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
		this.sourceManager = sourceManager;
	}

	private InputStream getStream(String url) throws Exception {
		var process = this.sourceManager.getProcess("-q", "-f", "webm", "-o", "-", url);
		var inputStream = process.getInputStream();
		return new BufferedInputStream(inputStream);
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		log.debug("Fetching yt-dlp stream URL: {}", trackInfo.uri);
		try (var stream = this.getStream(trackInfo.uri)) {
			processDelegate(new MatroskaAudioTrack(this.trackInfo, new NonSeekableInputStream(stream)), executor);
		}
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new YTDLPAudioTrack(this.trackInfo, this.albumName, this.albumUrl, this.artistUrl, this.artistArtworkUrl, this.previewUrl, this.isPreview, this.sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

}
