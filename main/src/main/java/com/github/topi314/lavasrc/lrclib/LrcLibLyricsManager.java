package com.github.topi314.lavasrc.lrclib;

import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class LrcLibLyricsManager implements AudioLyricsManager {

	private static final String API_BASE = "https://lrclib.net/api/";
	// [mm:ss.xx] followed by the line, the space after the timestamp is optional
	private static final Pattern SYNCED_LINE_PATTERN = Pattern.compile("^\\[(\\d+):(\\d+(?:\\.\\d+)?)](.*)$");

	private final HttpInterfaceManager httpInterfaceManager;

	public LrcLibLyricsManager() {
		this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "";
	}

	@Override
	public @Nullable AudioLyrics loadLyrics(@NotNull AudioTrack audioTrack) {
		try {
			return this.searchLyrics(null, audioTrack.getInfo().title, audioTrack.getInfo().author, null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private AudioLyrics searchLyrics(String query, String trackName, String artistName, String albumName) throws IOException {
		URI uri;
		try {
			var uriBuilder = new URIBuilder(API_BASE + "search");
			if (query != null) {
				uriBuilder.addParameter("q", query);
			}
			if (trackName != null) {
				uriBuilder.addParameter("track_name", trackName);
			}
			if (artistName != null) {
				uriBuilder.addParameter("artist_name", artistName);
			}
			if (albumName != null) {
				uriBuilder.addParameter("album_name", albumName);
			}
			uri = uriBuilder.build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		JsonBrowser json;
		try (var httpInterface = this.httpInterfaceManager.getInterface()) {
			json = LavaSrcTools.fetchResponseAsJson(httpInterface, new HttpGet(uri));
		}
		if (json == null || json.values().isEmpty()) {
			return null;
		}


		var result = json.index(0);
		var lyricsText = result.get("plainLyrics").text();
		var lyrics = new ArrayList<AudioLyrics.Line>();
		for (var line : result.get("syncedLyrics").safeText().split("\\n")) {
			var matcher = SYNCED_LINE_PATTERN.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			var timestamp = Duration.ofMinutes(Integer.parseInt(matcher.group(1)))
				.plusMillis(Math.round(Double.parseDouble(matcher.group(2)) * 1000));
			lyrics.add(new BasicAudioLyrics.BasicLine(
				timestamp,
				null,
				matcher.group(3).strip()
			));
		}

		return new BasicAudioLyrics("LRCLIB", "LRCLIB", lyricsText, lyrics);
	}

	@Override
	public void shutdown() {
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
