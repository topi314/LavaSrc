package com.github.topi314.lavasrc.ytdlp;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class YtdlpAudioSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable {

	public static final String SEARCH_PREFIX = "ytsearch:";

	private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.|m\\.|music\\.|)youtube\\.com/.*");
	private static final Pattern SHORT_URL_PATTERN = Pattern.compile("https?://(?:www\\.|)youtu\\.be/.*");

	private static final Logger log = LoggerFactory.getLogger(YtdlpAudioSourceManager.class);
	private final HttpInterfaceManager httpInterfaceManager;
	private String path;
	private int searchLimit;
	private String[] customLoadArgs;
	private String[] customPlaybackArgs;

	public YtdlpAudioSourceManager() {
		this("yt-dlp", 0, null, null);
	}

	public YtdlpAudioSourceManager(String path) {
		this(path, 0, null, null);
	}

	public YtdlpAudioSourceManager(String path, int searchLimit, String[] customLoadArgs, String[] customPlaybackArgs) {
		this.path = path;
		this.searchLimit = searchLimit == 0 ? 10 : searchLimit;
		if (customLoadArgs == null || customLoadArgs.length == 0) {
			this.customLoadArgs = new String[]{"-q", "--no-warnings", "--flat-playlist", "--skip-download", "-J"};
		} else {
			this.customLoadArgs = customLoadArgs;
		}
		if (customPlaybackArgs == null || customPlaybackArgs.length == 0) {
			this.customPlaybackArgs = new String[]{"-q", "--no-warnings", "-f", "bestaudio", "-J"};
		} else {
			this.customPlaybackArgs = customPlaybackArgs;
		}
		this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getSearchLimit() {
		return searchLimit;
	}

	public void setSearchLimit(int searchLimit) {
		this.searchLimit = searchLimit;
	}

	public String[] getCustomLoadArgs() {
		return customLoadArgs;
	}

	public void setCustomLoadArgs(String[] customLoadArgs) {
		this.customLoadArgs = customLoadArgs;
	}

	public String[] getCustomPlaybackArgs() {
		return customPlaybackArgs;
	}

	public void setCustomPlaybackArgs(String[] customPlaybackArgs) {
		this.customPlaybackArgs = customPlaybackArgs;
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "youtube";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new YtdlpAudioTrack(trackInfo,
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
				return this.getItem(String.format("ytsearch%d:%s", searchLimit, identifier.substring(SEARCH_PREFIX.length())));
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
		var entries = json.get("entries").values();
		if (entries.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		for (var entry : entries) {
			var ieKey = entry.get("ie_key").text();
			if (ieKey == null || !ieKey.equalsIgnoreCase("youtube")) {
				continue;
			}

			entry.put("extractor", json.get("extractor").text());
			var track = this.parseVideo(entry);
			if (track != null) {
				tracks.add(track);
			}
		}
		if (json.get("extractor").text().equals("youtube:search")) {
			return new BasicAudioPlaylist("Youtube Search: " + title, tracks, null, true);
		}

		var url = json.get("webpage_url").text();
		var thumbnailUrl = json.get("thumbnails").index(0).get("url").text();
		return new YtdlpAudioPlaylist(title, tracks, ExtendedAudioPlaylist.Type.PLAYLIST, url, thumbnailUrl, null, null);
	}

	public AudioTrack parseVideo(JsonBrowser json) {
		var title = json.get("title").text();
		var author = json.get("uploader").text();
		var identifier = json.get("id").text();
		var thumbnailUrl = json.get("thumbnail").text();
		var duration = json.get("duration").asLong(0) * 1000;
		var isLive = json.get("is_live").asBoolean(false);
		var url = json.get("url").text();

		return new YtdlpAudioTrack(
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
		var args = new ArrayList<>(List.of(this.customLoadArgs));
		args.add(identifier);
		var process = getProcess(args);
		var json = getProcessJsonOutput(process);

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

	Process getProcess(List<String> args) {
		var argList = new ArrayList<String>();
		argList.add(this.path);
		argList.addAll(args);

		log.debug("Starting yt-dlp with args: {}", argList);
		var processBuilder = new ProcessBuilder(argList);
		processBuilder.redirectErrorStream(true);

		try {
			return processBuilder.start();
		} catch (IOException e) {
			log.error("Failed to start yt-dlp process", e);
			throw new RuntimeException(e);
		}
	}

	JsonBrowser getProcessJsonOutput(Process process) throws IOException {
		try (var stream = new BufferedInputStream(process.getInputStream())) {
			var data = IOUtils.toString(stream, StandardCharsets.UTF_8);
			log.debug("yt-dlp process output: {}", data);
			int exitCode;
			try {
				exitCode = process.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("yt-dlp process was interrupted", e);
			}
			if (exitCode != 0) {
				throw new RuntimeException("Failed to retrieve item, error: " + data);
			}
			try {
				return JsonBrowser.parse(data);
			} catch (IOException e) {
				throw new IOException("Failed to parse yt-dlp output as JSON: " + data, e);
			}
		}
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
