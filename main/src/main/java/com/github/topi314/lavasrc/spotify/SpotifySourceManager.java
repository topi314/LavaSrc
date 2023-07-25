package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpotifySourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)(www\\.)?open\\.spotify\\.com/((?<region>[a-zA-Z-]+)/)?(user/(?<user>[a-zA-Z0-9-_]+)/)?(?<type>track|album|playlist|artist)/(?<identifier>[a-zA-Z0-9-_]+)");
	public static final String SEARCH_PREFIX = "spsearch:";
	public static final String RECOMMENDATIONS_PREFIX = "sprec:";
	public static final String PREVIEW_PREFIX = "spprev:";
	public static final long PREVIEW_LENGTH = 30000;
	public static final int PLAYLIST_MAX_PAGE_ITEMS = 100;
	public static final int ALBUM_MAX_PAGE_ITEMS = 50;
	public static final String API_BASE = "https://api.spotify.com/v1/";
	private static final Logger log = LoggerFactory.getLogger(SpotifySourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	private final String clientId;
	private final String clientSecret;
	private final String countryCode;
	private int playlistPageLimit = 6;
	private int albumPageLimit = 6;
	private String token;
	private Instant tokenExpire;

	public SpotifySourceManager(String[] providers, String clientId, String clientSecret, String countryCode, AudioPlayerManager audioPlayerManager) {
		this(clientId, clientSecret, countryCode, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public SpotifySourceManager(String clientId, String clientSecret, String countryCode, AudioPlayerManager audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		super(audioPlayerManager, mirroringAudioTrackResolver);

		if (clientId == null || clientId.isEmpty()) {
			throw new IllegalArgumentException("Spotify client id must be set");
		}
		this.clientId = clientId;

		if (clientSecret == null || clientSecret.isEmpty()) {
			throw new IllegalArgumentException("Spotify secret must be set");
		}
		this.clientSecret = clientSecret;

		if (countryCode == null || countryCode.isEmpty()) {
			countryCode = "US";
		}
		this.countryCode = countryCode;
	}

	public void setPlaylistPageLimit(int playlistPageLimit) {
		this.playlistPageLimit = playlistPageLimit;
	}

	public void setAlbumPageLimit(int albumPageLimit) {
		this.albumPageLimit = albumPageLimit;
	}

	@Override
	public String getSourceName() {
		return "spotify";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new SpotifyAudioTrack(trackInfo,
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
		var preview = reference.identifier.startsWith(PREVIEW_PREFIX);
		return this.loadItem(preview ? identifier.substring(PREVIEW_PREFIX.length()) : identifier, preview);
	}

	public AudioItem loadItem(String identifier, boolean preview) {
		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()).trim(), preview);
			}

			if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				return this.getRecommendations(identifier.substring(RECOMMENDATIONS_PREFIX.length()).trim(), preview);
			}

			var matcher = URL_PATTERN.matcher(identifier);
			if (!matcher.find()) {
				return null;
			}

			var id = matcher.group("identifier");
			switch (matcher.group("type")) {
				case "album":
					return this.getAlbum(id, preview);

				case "track":
					return this.getTrack(id, preview);

				case "playlist":
					return this.getPlaylist(id, preview);

				case "artist":
					return this.getArtist(id, preview);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	public void requestToken() throws IOException {
		var request = new HttpPost("https://accounts.spotify.com/api/token");
		request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((this.clientId + ":" + this.clientSecret).getBytes(StandardCharsets.UTF_8)));
		request.setEntity(new UrlEncodedFormEntity(List.of(new BasicNameValuePair("grant_type", "client_credentials")), StandardCharsets.UTF_8));

		var json = HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
		this.token = json.get("access_token").text();
		this.tokenExpire = Instant.now().plusSeconds(json.get("expires_in").asLong(0));
	}

	public String getToken() throws IOException {
		if (this.token == null || this.tokenExpire == null || this.tokenExpire.isBefore(Instant.now())) {
			this.requestToken();
		}
		return this.token;
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.addHeader("Authorization", "Bearer " + this.getToken());
		return HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	public AudioItem getSearch(String query, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=track");
		if (json == null || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Search results for: " + query, this.parseTrackItems(json.get("tracks"), preview), null, true);
	}

	public AudioItem getRecommendations(String query, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "recommendations?" + query);
		if (json == null || json.get("tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new SpotifyAudioPlaylist("Spotify Recommendations:", this.parseTracks(json, preview), "recommendations", null, null, null);
	}

	public AudioItem getAlbum(String id, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "albums/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		JsonBrowser page;
		var offset = 0;
		var pages = 0;
		do {
			page = this.getJson(API_BASE + "albums/" + id + "/tracks?limit=" + ALBUM_MAX_PAGE_ITEMS + "&offset=" + offset);
			offset += ALBUM_MAX_PAGE_ITEMS;

			var tracksPage = this.getJson(API_BASE + "tracks/?ids=" + page.get("items").values().stream().map(track -> track.get("id").text()).collect(Collectors.joining(",")));

			for (var track : tracksPage.get("tracks").values()) {
				var albumJson = JsonBrowser.newMap();
				albumJson.put("name", json.get("name"));
				track.put("album", albumJson);

				var artistsJson = JsonBrowser.newList();
				artistsJson.add(json.get("artists").index(0));
			}
			tracks.addAll(this.parseTracks(tracksPage, preview));
		}
		while (page.get("next").text() != null && ++pages < this.albumPageLimit);

		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new SpotifyAudioPlaylist(json.get("name").text(), tracks, "album", json.get("external_urls").get("spotify").text(), json.get("images").index(0).get("url").text(), json.get("artists").index(0).get("name").text());

	}

	public AudioItem getPlaylist(String id, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "playlists/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		JsonBrowser page;
		var offset = 0;
		var pages = 0;
		do {
			page = this.getJson(API_BASE + "playlists/" + id + "/tracks?limit=" + PLAYLIST_MAX_PAGE_ITEMS + "&offset=" + offset);
			offset += PLAYLIST_MAX_PAGE_ITEMS;

			for (var value : page.get("items").values()) {
				var track = value.get("track");
				if (track.isNull() || track.get("is_local").asBoolean(false)) {
					continue;
				}
				tracks.add(this.parseTrack(track, preview));
			}

		}
		while (page.get("next").text() != null && ++pages < this.playlistPageLimit);

		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new SpotifyAudioPlaylist(json.get("name").text(), tracks, "playlist", json.get("external_urls").get("spotify").text(), json.get("images").index(0).get("url").text(), json.get("owner").get("display_name").text());

	}

	public AudioItem getArtist(String id, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "artists/" + id + "/top-tracks?market=" + this.countryCode);
		if (json == null || json.get("tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new SpotifyAudioPlaylist(json.get("tracks").index(0).get("artists").index(0).get("name").text() + "'s Top Tracks", this.parseTracks(json, preview), "artist", json.get("tracks").index(0).get("external_urls").get("spotify").text(), json.get("tracks").index(0).get("album").get("images").index(0).get("url").text(), json.get("tracks").index(0).get("artists").index(0).get("name").text());
	}

	public AudioItem getTrack(String id, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "tracks/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json, preview);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview) {
		var tracks = new ArrayList<AudioTrack>();
		for (var value : json.get("tracks").values()) {
			tracks.add(this.parseTrack(value, preview));
		}
		return tracks;
	}

	private List<AudioTrack> parseTrackItems(JsonBrowser json, boolean preview) {
		var tracks = new ArrayList<AudioTrack>();
		for (var value : json.get("items").values()) {
			if (value.get("is_local").asBoolean(false)) {
				continue;
			}
			tracks.add(this.parseTrack(value, preview));
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json, boolean preview) {
		return new SpotifyAudioTrack(
				new AudioTrackInfo(
						json.get("name").text(),
						json.get("artists").index(0).get("name").text(),
						preview ? PREVIEW_LENGTH : json.get("duration_ms").asLong(0),
						json.get("id").text(),
						false,
						json.get("external_urls").get("spotify").text(),
						json.get("album").get("images").index(0).get("url").text(),
						json.get("external_ids").get("isrc").text()
				),
				json.get("album").get("name").text(),
				json.get("album").get("external_urls").get("spotify").text(),
				json.get("artists").index(0).get("external_urls").get("spotify").text(),
				json.get("artists").index(0).get("images").index(0).get("url").text(),
				json.get("preview_url").text(),
				preview,
				this
		);
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

}
