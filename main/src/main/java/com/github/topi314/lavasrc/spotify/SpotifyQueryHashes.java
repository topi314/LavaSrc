package com.github.topi314.lavasrc.spotify;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;


public class SpotifyQueryHashes {

	private static final Logger log = LoggerFactory.getLogger(SpotifyQueryHashes.class);

	private static final String USER_AGENT =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
		"(KHTML, like Gecko) Chrome/134.0.6998.178 Spotify/1.2.65.255 Safari/537.36";

	private static final String SERVICE_WORKER_URL = "https://open.spotify.com/service-worker.js";
	private static final String CDN_BASE = "https://open.spotifycdn.com/cdn/build/web-player/";

	public static final String DEFAULT_SEARCH_HASH =
		"fcad5a3e0d5af727fb76966f06971c19cfa2275e6ff7671196753e008611873c";
	public static final String DEFAULT_TRACK_HASH =
		"612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294";
	public static final String DEFAULT_PLAYLIST_HASH =
		"bb67e0af06e8d6f52b531f97468ee4acd44cd0f82b988e15c2ea47b1148efc77";
	public static final String DEFAULT_ALBUM_HASH =
		"b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10";
	public static final String DEFAULT_SEARCH_TOP_RESULTS_HASH =
		"63a93cc04f6d8dea84a85de315e43f396a76cb681500de9ac5ccf5fc618c84cb";
	public static final String DEFAULT_ARTIST_HASH =
		"35648a112beb1794e39ab931365f6ae4a8d45e65396d641eeda94e4003d41497";

	private volatile String searchHash = DEFAULT_SEARCH_HASH;
	private volatile String trackHash = DEFAULT_TRACK_HASH;
	private volatile String playlistHash = DEFAULT_PLAYLIST_HASH;
	private volatile String albumHash = DEFAULT_ALBUM_HASH;
	private volatile String searchTopResultsHash = DEFAULT_SEARCH_TOP_RESULTS_HASH;
	private volatile String artistHash = DEFAULT_ARTIST_HASH;

	public String getSearchHash() { return searchHash; }
	public String getTrackHash() { return trackHash; }
	public String getPlaylistHash() { return playlistHash; }
	public String getAlbumHash() { return albumHash; }
	public String getSearchTopResultsHash() { return searchTopResultsHash; }
	public String getArtistHash() { return artistHash; }


	public void refresh() {
		try (var client = HttpClients.createDefault()) {
			var swText = fetchText(client, SERVICE_WORKER_URL);
			if (swText == null) {
				log.warn("Could not fetch Spotify service-worker.js — keeping fallback hashes");
				return;
			}

			searchHash = scrapeHash(client, swText,
				"xpui-routes-search\\.[a-f0-9]{8,}\\.js",
				"\"searchDesktop\",\"(?:query|mutation)\",\"([^\"]+)\"",
				DEFAULT_SEARCH_HASH, "searchDesktop");

			trackHash = scrapeHash(client, swText,
				"web-player\\.[a-f0-9]{8,}\\.js",
				"\"getTrack\",\"(?:query|mutation)\",\"([^\"]+)\"",
				DEFAULT_TRACK_HASH, "getTrack");

			playlistHash = scrapeHash(client, swText,
				"web-player\\.[a-f0-9]{8,}\\.js",
				"\"fetchPlaylist\",\"(?:query|mutation)\",\"([^\"]+)\"",
				DEFAULT_PLAYLIST_HASH, "fetchPlaylist");

			albumHash = scrapeHash(client, swText,
				"web-player\\.[a-f0-9]{8,}\\.js",
				"\"getAlbum\",\"(?:query|mutation)\",\"([^\"]+)\"",
				DEFAULT_ALBUM_HASH, "getAlbum");

			searchTopResultsHash = scrapeHash(client, swText,
				"xpui-routes-search\\.[a-f0-9]{8,}\\.js",
				"\"searchTopResultsList\",\"(?:query|mutation)\",\"([^\"]+)\"",
				DEFAULT_SEARCH_TOP_RESULTS_HASH, "searchTopResultsList");

			artistHash = scrapeHash(client, swText,
				"web-player\\.[a-f0-9]{8,}\\.js",
				"\"queryArtistOverview\",\"(?:query|mutation)\",\"([^\"]+)\"",
				DEFAULT_ARTIST_HASH, "queryArtistOverview");

			log.debug("Spotify query hashes refreshed successfully");
		} catch (IOException e) {
			log.warn("Failed to refresh Spotify query hashes — keeping fallback hashes", e);
		}
	}

	@NotNull
	private String scrapeHash(
		CloseableHttpClient client,
		String swText,
		String filePattern,
		String hashPattern,
		String fallback,
		String name
	) {
		var fileRe = Pattern.compile(filePattern);
		var hashRe = Pattern.compile(hashPattern);

		var fileMatcher = fileRe.matcher(swText);
		var files = new java.util.ArrayList<String>();
		while (fileMatcher.find()) {
			var f = fileMatcher.group();
			if (!files.contains(f)) {
				files.add(f);
			}
		}

		if (files.isEmpty()) {
			log.debug("No JS file found for hash '{}', using fallback", name);
			return fallback;
		}

		for (int i = files.size() - 1; i >= 0; i--) {
			var jsText = fetchText(client, CDN_BASE + files.get(i));
			if (jsText == null) continue;

			var m = hashRe.matcher(jsText);
			if (m.find() && !m.group(1).isEmpty()) {
				return m.group(1);
			}
		}

		log.debug("Hash '{}' not found in any JS file, using fallback", name);
		return fallback;
	}

	private static String fetchText(CloseableHttpClient client, String url) {
		try {
			var request = new HttpGet(url);
			request.setHeader("User-Agent", USER_AGENT);
			try (var response = client.execute(request)) {
				var entity = response.getEntity();
				if (entity == null) return null;
				var bytes = EntityUtils.toByteArray(entity);
				// Limit to 10 MB
				if (bytes.length > 10 * 1024 * 1024) return null;
				return new String(bytes, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			log.debug("Failed to fetch {}: {}", url, e.getMessage());
			return null;
		}
	}
}
