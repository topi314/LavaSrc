package com.github.topi314.lavasrc.yandexmusic;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class YandexMusicSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable {
	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?music\\.yandex\\.(ru|com|kz|by)/(?<type1>artist|album|track)/(?<identifier>[0-9]+)(/(?<type2>track)/(?<identifier2>[0-9]+))?/?");
	public static final Pattern URL_PLAYLIST_PATTERN = Pattern.compile("(https?://)?music\\.yandex\\.(ru|com|kz|by)/users/(?<identifier>[0-9A-Za-z@.-]+)/playlists/(?<identifier2>[0-9]+)/?");
	public static final String SEARCH_PREFIX = "ymsearch:";
	public static final String RECOMMENDATIONS_PREFIX = "ymrec:";
	public static final String PUBLIC_API_BASE = "https://api.music.yandex.net";
	public static final int ARTIST_MAX_PAGE_ITEMS = 10;
	public static final int PLAYLIST_MAX_PAGE_ITEMS = 100;
	public static final int ALBUM_MAX_PAGE_ITEMS = 50;

	private static final Logger log = LoggerFactory.getLogger(YandexMusicSourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager;

	private final String accessToken;
	private int artistLoadLimit;
	private int albumLoadLimit;
	private int playlistLoadLimit;

	public YandexMusicSourceManager(String accessToken) {
		if (accessToken == null || accessToken.isEmpty()) {
			throw new IllegalArgumentException("Yandex Music accessToken must be set");
		}
		this.accessToken = accessToken;
		this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	}

	public void setArtistLoadLimit(int artistLimit) {
		this.artistLoadLimit = artistLimit;
	}
	public void setAlbumLoadLimit(int albumLimit) {
		this.albumLoadLimit = albumLimit;
	}
	public void setPlaylistLoadLimit(int playlistLimit) {
		this.playlistLoadLimit = playlistLimit;
	}

	@Override
	public String getSourceName() {
		return "yandexmusic";
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		try {
			if (reference.identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
			}

			if (reference.identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				return this.getRecommendations(reference.identifier.substring(RECOMMENDATIONS_PREFIX.length()));
			}

			var matcher = URL_PATTERN.matcher(reference.identifier);
			if (matcher.find()) {
				switch (matcher.group("type1")) {
					case "album":
						if (matcher.group("type2") != null) {
							var trackId = matcher.group("identifier2");
							return this.getTrack(trackId);
						}
						var albumId = matcher.group("identifier");
						return this.getAlbum(albumId);
					case "artist":
						var artistId = matcher.group("identifier");
						return this.getArtist(artistId);
					case "track":
						var trackId = matcher.group("identifier");
						return this.getTrack(trackId);
				}
				return null;
			}
			matcher = URL_PLAYLIST_PATTERN.matcher(reference.identifier);
			if (matcher.find()) {
				var userId = matcher.group("identifier");
				var playlistId = matcher.group("identifier2");
				return this.getPlaylist(userId, playlistId);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	private AudioItem getRecommendations(String identifier) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/tracks/"+identifier+"/similar");
		if (json.isNull() || json.get("result").isNull() || json.get("result").get("similarTracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("result").get("similarTracks"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var trackInfo = json.get("result").get("track");
		return new BasicAudioPlaylist("Yandex Music Recommendations For Track: " + trackInfo.get("title").text(), tracks, null, true);
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/search?text=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=track&page=0");
		if (json.isNull() || json.get("result").get("tracks").isNull()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("result").get("tracks").get("results"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new BasicAudioPlaylist("Yandex Music Search: " + query, tracks, null, true);
	}

	private AudioItem getAlbum(String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/albums/" + id + "/with-tracks?page-size=" + ALBUM_MAX_PAGE_ITEMS * albumLoadLimit);
		if (json.isNull() || json.get("result").isNull()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = new ArrayList<AudioTrack>();
		for (var volume : json.get("result").get("volumes").values()) {
			for (var track : volume.values()) {
				var parsedTrack = this.parseTrack(track);
				if (parsedTrack != null) {
					tracks.add(parsedTrack);
				}
			}
		}
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var author = json.get("result").get("artists").values().get(0).get("name").text();
		return new YandexMusicAudioPlaylist(
				json.get("result").get("title").text(),
				tracks,
				ExtendedAudioPlaylist.Type.ALBUM,
				"https://music.yandex.ru/album/" + id,
				this.parseCoverUri(json.get("result")),
				author,
				tracks.size()
		);
	}

	private AudioItem getTrack(String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/tracks/" + id);
		if (json.isNull() || json.get("result").values().get(0).get("available").text().equals("false")) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json.get("result").values().get(0));
	}

	private AudioItem getArtist(String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/artists/" + id + "/tracks?page-size=" + ARTIST_MAX_PAGE_ITEMS * artistLoadLimit);
		if (json.isNull() || json.get("result").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(json.get("result").get("tracks"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artistJsonResponse = this.getJson(PUBLIC_API_BASE + "/artists/" + id);
		var artistJson = artistJsonResponse.get("result").get("artist");
		var author = artistJson.get("name").text();

		return new YandexMusicAudioPlaylist(
				author + "'s Top Tracks",
				tracks,
				ExtendedAudioPlaylist.Type.ARTIST,
				"https://music.yandex.ru/artist/" + id,
				parseCoverUri(artistJson),
				author,
				tracks.size()
		);
	}

	private AudioItem getPlaylist(String userString, String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/users/" + userString + "/playlists/" + id + "?page-size=" + PLAYLIST_MAX_PAGE_ITEMS * playlistLoadLimit);
		if (json.isNull() || json.get("result").isNull() || json.get("result").get("tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("result").get("tracks"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		String playlistTitle;
		if (json.get("result").get("kind").text().equals("3")) {
			var ownerJson = json.get("result").get("owner");
			var ownerName = ownerJson.get("name").isNull() ? ownerJson.get("login").text() : ownerJson.get("name").text();
			playlistTitle = ownerName + "'s liked songs";
		} else {
			playlistTitle = json.get("result").get("title").text();
		}
		var author = json.get("result").get("owner").get("name").text();
		return new YandexMusicAudioPlaylist(
				playlistTitle,
				tracks,
				ExtendedAudioPlaylist.Type.PLAYLIST,
				"https://music.yandex.ru/users/" + userString + "/playlists/" + id,
				this.parseCoverUri(json.get("result")),
				author,
				tracks.size()
		);
	}

	private List<JsonBrowser> getTracks(String trackIds) throws IOException {
		return getJson(PUBLIC_API_BASE + "/tracks?track-ids=" + URLEncoder.encode(trackIds, StandardCharsets.UTF_8)).get("result").values();
	}

	private String getTrackIds(List<JsonBrowser> tracksToParse) {
		return tracksToParse.stream()
				.map(node -> node.get("id").text())
				.collect(Collectors.joining(","));
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		request.setHeader("Authorization", "OAuth " + this.accessToken);
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	public String getDownloadStrings(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		request.setHeader("Authorization", "OAuth " + this.accessToken);
		return HttpClientTools.fetchResponseLines(this.httpInterfaceManager.getInterface(), request, "downloadinfo-xml-page")[0];
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) throws IOException {
		var tracksToParse = json.values();
		if (json.values().get(0).get("available").isNull()) {
			 tracksToParse = getTracks(getTrackIds(tracksToParse));
		}

		var tracks = new ArrayList<AudioTrack>();
		for (var track : tracksToParse) {
			var parsedTrack = track.get("track").isNull() ? this.parseTrack(track) : this.parseTrack(track.get("track"));
			if (parsedTrack != null) {
				tracks.add(parsedTrack);
			}
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json) {
		if (!json.get("available").asBoolean(false)) {
			return null;
		}
		var id = json.get("id").text();
		var artist = parseArtist(json);

		String albumName = null;
		String albumUrl = null;
		if (!json.get("albums").values().isEmpty()) {
			var album = json.get("albums").values().get(0);
			albumName = album.get("title").text();
			albumUrl = "https://music.yandex.ru/album/" + album.get("id").text();
		}

		String artistUrl = null;
		String artistArtworkUrl = null;
		if (!json.get("artists").values().isEmpty()) {
			var firstArtist = json.get("artists").values().get(0);
			artistUrl = "https://music.yandex.ru/artist/" + firstArtist.get("id").text();
			artistArtworkUrl = parseCoverUri(firstArtist);
		}
		return new YandexMusicAudioTrack(
				new AudioTrackInfo(
						json.get("title").text(),
						artist,
						json.get("durationMs").as(Long.class),
						id,
						false,
						"https://music.yandex.ru/track/" + id,
						this.parseCoverUri(json),
						null
				),
				albumName,
				albumUrl,
				artistUrl,
				artistArtworkUrl,
				this
		);
	}

	private String parseArtist(JsonBrowser json) {
		if (!json.get("major").isNull() && json.get("major").get("name").text().equals("PODCASTS")) {
			return json.get("albums").values().get(0).get("title").text();
		}

		if (!json.get("artists").values().isEmpty()) {
			return extractArtists(json.get("artists"));
		}

		if (!json.get("matchedTrack").isNull()) {
			return extractArtists(json.get("matchedTrack").get("artists"));
		}

		return "Unknown";
	}

	private String extractArtists(JsonBrowser artistNode) {
		return artistNode.values().stream()
				.map(node -> node.get("name").text())
				.collect(Collectors.joining(", "));
	}

	private String parseCoverUri(JsonBrowser objectJson) {
		if (!objectJson.get("ogImage").isNull()) {
			return formatCoverUri(objectJson.get("ogImage").text());
		}
		if (!objectJson.get("coverUri").isNull()) {
			return formatCoverUri(objectJson.get("coverUri").text());
		}

		var coverJson = objectJson.get("cover");
		if (!coverJson.isNull()) {
			if (!coverJson.get("uri").isNull()) {
				return formatCoverUri(coverJson.get("uri").text());
			} else if (!coverJson.get("itemsUri").values().isEmpty()) {
				return formatCoverUri(coverJson.get("itemsUri").values().get(0).text());
			}
		}

		return null;
	}

	private String formatCoverUri(String coverUri) {
		return coverUri != null ? "https://" + coverUri.replace("%%", "400x400") : null;
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new YandexMusicAudioTrack(trackInfo,
				extendedAudioTrackInfo.albumName,
				extendedAudioTrackInfo.albumUrl,
				extendedAudioTrackInfo.artistUrl,
				extendedAudioTrackInfo.artistArtworkUrl,
				this
		);
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		this.httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		this.httpInterfaceManager.configureBuilder(configurator);
	}

	@Override
	public void shutdown() {
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}
}
