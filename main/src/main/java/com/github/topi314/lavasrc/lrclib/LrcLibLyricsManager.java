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

public class LrcLibLyricsManager implements AudioLyricsManager {

	private static final String API_BASE = "https://lrclib.net/api/";

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
			var parts = line.split(" ", 2);
			if (parts.length < 2) {
				continue;
			}
			var timePart = parts[0].substring(1, parts[0].length() - 1);
			var timeParts = timePart.split(":");
			if (timeParts.length != 2) {
				continue;
			}
			Duration timestamp;
			try {
				timestamp = Duration.ofMinutes(Integer.parseInt(timeParts[0]))
					.plusMillis((long) (Double.parseDouble(timeParts[1]) * 1000));
			} catch (NumberFormatException e) {
				throw new RuntimeException("Invalid timestamp format: " + timePart, e);
			}
			lyrics.add(new BasicAudioLyrics.BasicLine(
				timestamp,
				null,
				parts[1]
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
