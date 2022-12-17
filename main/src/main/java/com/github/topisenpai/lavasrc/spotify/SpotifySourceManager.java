package com.github.topisenpai.lavasrc.spotify;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
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

public class SpotifySourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?open\\.spotify\\.com/(user/[a-zA-Z0-9-_]+/)?(?<type>track|album|playlist|artist)/(?<identifier>[a-zA-Z0-9-_]+)");
	public static final String SEARCH_PREFIX = "spsearch:";
	public static final String RECOMMENDATIONS_PREFIX = "sprec:";
	public static final int PLAYLIST_MAX_PAGE_ITEMS = 100;
	public static final int ALBUM_MAX_PAGE_ITEMS = 50;
	public static final String API_BASE = "https://api.spotify.com/v1/";
	private static final Logger log = LoggerFactory.getLogger(SpotifySourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	private final String clientId;
	private final String clientSecret;
	private final String countryCode;
	private String token;
	private Instant tokenExpire;

	public SpotifySourceManager(String[] providers, String clientId, String clientSecret, String countryCode, AudioPlayerManager audioPlayerManager) {
		super(providers, audioPlayerManager);

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

	@Override
	public String getSourceName() {
		return "spotify";
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) {

	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
		return new SpotifyAudioTrack(trackInfo, this);
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		try {
			if (reference.identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
			}

			if (reference.identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				return this.getRecommendations(reference.identifier.substring(RECOMMENDATIONS_PREFIX.length()).trim());
			}

			var matcher = URL_PATTERN.matcher(reference.identifier);
			if (!matcher.find()) {
				return null;
			}

			var id = matcher.group("identifier");
			switch (matcher.group("type")) {
				case "album":
					return this.getAlbum(id);

				case "track":
					return this.getTrack(id);

				case "playlist":
					return this.getPlaylist(id);

				case "artist":
					return this.getArtist(id);
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

	public AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(API_BASE + "search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=track");
		if (json == null || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Search results for: " + query, this.parseTrackItems(json.get("tracks")), null, true);
	}

	public AudioItem getRecommendations(String query) throws IOException {
		var json = this.getJson(API_BASE + "recommendations?" + query);
		if (json == null || json.get("tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Spotify Recommendations:", this.parseTracks(json), null, false);
	}

	public AudioItem getAlbum(String id) throws IOException {
		var json = this.getJson(API_BASE + "albums/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		JsonBrowser page;
		var offset = 0;
		do {
			page = this.getJson(API_BASE + "albums/" + id + "/tracks?limit=" + ALBUM_MAX_PAGE_ITEMS + "&offset=" + offset);
			offset += ALBUM_MAX_PAGE_ITEMS;

			tracks.addAll(this.parseTrackItems(page));
		}
		while (page.get("next").text() != null);

		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = json.get("images").index(0).get("url").text();
		var author = json.get("author").get("name").text();
		return new SpotifyAudioPlaylist(json.get("name").text(), tracks, "album", id, artworkUrl, author);

	}

	public AudioItem getPlaylist(String id) throws IOException {
		var json = this.getJson(API_BASE + "playlists/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		JsonBrowser page;
		var offset = 0;
		do {
			page = this.getJson(API_BASE + "playlists/" + id + "/tracks?limit=" + PLAYLIST_MAX_PAGE_ITEMS + "&offset=" + offset);
			offset += PLAYLIST_MAX_PAGE_ITEMS;

			for (var value : page.get("items").values()) {
				var track = value.get("track");
				if (track.isNull() || track.get("is_local").asBoolean(false)) {
					continue;
				}
				tracks.add(this.parseTrack(track));
			}

		}
		while (page.get("next").text() != null);

		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = json.get("images").index(0).get("url").text();
		var author = json.get("owner").get("string").text();
		return new SpotifyAudioPlaylist(json.get("name").text(), tracks, "playlist", id, artworkUrl, author);

	}

	public AudioItem getArtist(String id) throws IOException {
		var json = this.getJson(API_BASE + "artists/" + id + "/top-tracks?market=" + this.countryCode);
		if (json == null || json.get("tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = json.get("tracks").index(0).get("album").get("images").index(0).get("url").text();
		var author = json.get("tracks").index(0).get("artists").index(0).get("name").text();
		return new SpotifyAudioPlaylist(author + "'s Top Tracks", this.parseTracks(json), "artist", id, artworkUrl, author);
	}

	public AudioItem getTrack(String id) throws IOException {
		var json = this.getJson(API_BASE + "tracks/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		return parseTrack(json);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var value : json.get("tracks").values()) {
			tracks.add(this.parseTrack(value));
		}
		return tracks;
	}

	private List<AudioTrack> parseTrackItems(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var value : json.get("items").values()) {
			if (value.get("is_local").asBoolean(false)) {
				continue;
			}
			tracks.add(this.parseTrack(value));
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json) {
		return new SpotifyAudioTrack(
				new AudioTrackInfo(
						json.get("name").text(),
						json.get("artists").index(0).get("name").text(),
						json.get("duration_ms").asLong(0),
						json.get("id").text(),
						false,
						json.get("external_urls").get("spotify").text(),
						json.get("album").get("images").index(0).get("url").text(),
						json.get("external_ids").get("isrc").text()
				),
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
