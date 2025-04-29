package com.github.topi314.lavasrc.ytdlp;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class YTDLPAudioSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable {

	public static final String SEARCH_PREFIX = "ytsearch:";

	private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.|m\\.|music\\.|)youtube\\.com/.*");
	private static final Pattern SHORT_URL_PATTERN = Pattern.compile("https?://(?:www\\.|)youtu\\.be/.*");

	private static final Logger log = LoggerFactory.getLogger(YTDLPAudioSourceManager.class);

	private final String path;
	private final HttpInterfaceManager httpInterfaceManager;


	public YTDLPAudioSourceManager(String path) {
		this.path = path;
		this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "youtube";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new YTDLPAudioTrack(trackInfo,
			extendedAudioTrackInfo.albumName,
			extendedAudioTrackInfo.albumUrl,
			extendedAudioTrackInfo.artistUrl,
			extendedAudioTrackInfo.artistArtworkUrl,
			extendedAudioTrackInfo.previewUrl,
			extendedAudioTrackInfo.isPreview,
			this
		);
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		var identifier = reference.identifier;
		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return this.getItem(identifier);
			}

			if (URL_PATTERN.matcher(identifier).matches() || SHORT_URL_PATTERN.matcher(identifier).matches()) {
				return this.getItem(identifier);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	public AudioItem parsePlaylist(JsonBrowser json) {
		var title = json.get("title").text();
		var url = json.get("url").text();
		var thumbnailUrl = json.get("thumbnails").index(0).get("url").text();
		var entries = json.get("entries").values();
		var tracks = new ArrayList<AudioTrack>();
		for (var entry : entries) {
			entry.put("extractor", json.get("extractor").text());
			var track = this.parseVideo(entry);
			if (track != null) {
				tracks.add(track);
			}
		}

		return new YTDLPAudioPlaylist("Youtube Search: " + title, tracks, ExtendedAudioPlaylist.Type.PLAYLIST, url, thumbnailUrl, null, null);
	}

	public AudioTrack parseVideo(JsonBrowser json) {
		var title = json.get("title").text();
		var author = json.get("uploader").text();
		var identifier = json.get("id").text();
		var thumbnailUrl = json.get("thumbnail").text();
		var duration = json.get("duration").asLong(0) * 1000;
		var isLive = json.get("is_live").asBoolean(false);
		var url = json.get("url").text();

		return new YTDLPAudioTrack(
			new AudioTrackInfo(title, author, duration, identifier, isLive, url, thumbnailUrl, null),
			null,
			null,
			null,
			null,
			null,
			false,
			this
		);
	}

	public AudioItem getItem(String identifier) throws IOException {
		var process = getProcess("--flat-playlist", "-J", identifier);
		var json = JsonBrowser.parse(process.getInputStream());

		var type = json.get("_type").text();
		switch (type) {
			case "playlist":
				return this.parsePlaylist(json);
			case "video":
				json.put("url", json.get("webpage_url").text());
				return this.parseVideo(json);
		}
		return null;
	}

	Process getProcess(String... args) throws IOException {
		var argList = new ArrayList<String>();
		argList.add(this.path);
		argList.addAll(List.of(args));

		var processBuilder = new ProcessBuilder(argList);
		processBuilder.redirectErrorStream(true);

		return processBuilder.start();
	}

	@Override
	public void shutdown() {
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		this.httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		this.httpInterfaceManager.configureBuilder(configurator);
	}

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}

}
