package com.github.topi314.lavasrc.qobuz;

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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class QobuzAudioSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable {

	public static final String SEARCH_PREFIX = "qbsearch:";
	public static final String ISRC_PREFIX = "qbisrc:";
	public static final String RECOMMENDATIONS_PREFIX = "qbrec:";
	private static final Logger log = LoggerFactory.getLogger(QobuzAudioSourceManager.class);
	private static final String WEB_PLAYER_BASE_URL = "https://play.qobuz.com";
	private static final String API_URL = "https://www.qobuz.com/api.json/0.2/";
	private static final String DEFAULT_APP_ID = "950096963";
	private static final String DEFAULT_APP_SECRET = "979549437fcc4a3faad4867b5cd25dcb";
	private static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.|play\\.|open\\.)?qobuz\\.com/(?:(?:[a-z]{2}-[a-z]{2}/)?(?<type>album|playlist|track|artist)/(?:.+?/)?(?<id>[a-zA-Z0-9]+)|(?<type2>playlist)/(?<id2>\\d+))");
	private static final int ALBUM_LOAD_LIMIT = 500;
	private static final int PLAYLIST_LOAD_LIMIT = 1000;
	private static final Pattern BUNDLE_PATTERN = Pattern.compile("<script src=\"(?<bundleJS>/resources/\\d+\\.\\d+\\.\\d+-[a-z]\\d{3}/bundle\\.js)\"");
	private static final Pattern APP_ID_PATTERN = Pattern.compile("production:\\{api:\\{appId:\"(?<appID>.*?)\",appSecret:");
	private static final Pattern SEED_PATTERN = Pattern.compile("\\):[a-z]\\.initialSeed\\(\"(?<seed>.*?)\",window\\.utimezone\\.(?<timezone>[a-z]+)\\)");

	private final HttpInterfaceManager httpInterfaceManager;
	private String appId;
	private String appSecret;
	private String userOauthToken;

	public QobuzAudioSourceManager(String userOauthToken, String appId, String appSecret) {
		if (userOauthToken == null || userOauthToken.isEmpty()) {
			throw new IllegalArgumentException("User Oauth token cannot be null or empty.");
		}
		this.userOauthToken = userOauthToken;
		this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
		if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
			this.fetchAppInfo();
		} else {
			this.appId = appId;
			this.appSecret = appSecret;
		}
	}

	public String fetchBundleString() throws IOException {
		try (var httpInterface = httpInterfaceManager.getInterface()) {
			HttpGet httpGet = new HttpGet(WEB_PLAYER_BASE_URL + "/login");
			String bundleUrl;
			try (var response = httpInterface.execute(httpGet)) {
				var bundleMatcher = BUNDLE_PATTERN.matcher(EntityUtils.toString(response.getEntity()));
				if (!bundleMatcher.find()) {
					throw new IllegalStateException("Failed to extract bundle.js URL");
				}
				bundleUrl = WEB_PLAYER_BASE_URL + bundleMatcher.group("bundleJS");
			}
			var bundleRequest = new HttpGet(bundleUrl);
			try (var response = httpInterface.execute(bundleRequest)) {
				return EntityUtils.toString(response.getEntity());
			}
		}
	}

	public String getWebPlayerAppId(String bundleJsContent) {
		var appIdMatcher = APP_ID_PATTERN.matcher(bundleJsContent);
		if (!appIdMatcher.find()) {
			throw new IllegalStateException("Failed to extract app_id from bundle.js");
		}
		return appIdMatcher.group("appID");
	}

	public String getWebPlayerAppSecret(String bundleJsContent) {
		var seedMatcher = SEED_PATTERN.matcher(bundleJsContent);
		if (!seedMatcher.find()) {
			throw new IllegalStateException("Failed to extract seed and timezone from bundle.js");
		}

		var seed = seedMatcher.group("seed");
		var productionTimezone = capitalize(seedMatcher.group("timezone"));
		var infoExtrasRegex = Pattern.compile("timezones:\\[.*?name:.*?/" + productionTimezone + "\",info:\"(?<info>.*?)\",extras:\"(?<extras>.*?)\"");
		var infoExtrasMatcher = infoExtrasRegex.matcher(bundleJsContent);
		if (!infoExtrasMatcher.find()) {
			throw new IllegalStateException("Failed to extract info and extras for timezone " + productionTimezone + " from bundle.js");
		}
		var base64EncodedAppSecret = seed + infoExtrasMatcher.group("info") + infoExtrasMatcher.group("extras");
		base64EncodedAppSecret = base64EncodedAppSecret.substring(0, base64EncodedAppSecret.length() - 44);
		return new String(Base64.getDecoder().decode(base64EncodedAppSecret));
	}

	private static String capitalize(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "qobuz";
	}

	public String getAppSecret() {
		return this.appSecret;
	}

	public void setAppSecret(String appSecret) {
		this.appSecret = appSecret;
	}

	public void setUserOauthToken(String userOauthToken) {
		this.userOauthToken = userOauthToken;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new QobuzAudioTrack(trackInfo,
			extendedAudioTrackInfo.albumName,
			extendedAudioTrackInfo.albumUrl,
			extendedAudioTrackInfo.artistUrl,
			extendedAudioTrackInfo.artistArtworkUrl,
			this);
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		var identifier = reference.identifier;

		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()));
			}
			if (identifier.startsWith(ISRC_PREFIX)) {
				return this.getTrackByISRC(identifier.substring(ISRC_PREFIX.length()));
			}
			if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				return this.getRecommendations(identifier.substring(RECOMMENDATIONS_PREFIX.length()));
			}
			var matcher = URL_PATTERN.matcher(identifier);
			if (!matcher.find()) {
				return null;
			}
			var type = matcher.group("type");
			var id = matcher.group("id");
			if (type == null) {
				type = matcher.group("type2");
				id = matcher.group("id2");
			}

			switch (type) {
				case "playlist":
					return this.getPlaylist(id);
				case "album":
					return this.getAlbum(id);
				case "track":
					return this.getTrack(id);
				case "artist":
					return this.getArtist(id);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		request.setHeader("x-app-id", this.appId);
		request.setHeader("x-user-auth-token", this.userOauthToken);
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.values()) {
			tracks.add(this.parseTrack(track));
		}
		return tracks;
	}

	private void fetchAppInfo() {
		try {
			var bundleJsContent = this.fetchBundleString();
			this.appId = this.getWebPlayerAppId(bundleJsContent);
			this.appSecret = this.getWebPlayerAppSecret(bundleJsContent);
			log.info("Fetched Qobuz App ID :{} and App Secret :{}", this.appId, this.appSecret);
		} catch (Exception e) {
			log.error("Failed to fetch app info falling back to hardcoded values.", e);
			this.appId = DEFAULT_APP_ID;
			this.appSecret = DEFAULT_APP_SECRET;
		}
	}

	private AudioTrack parseTrack(JsonBrowser json) {
		var identifier = json.get("id").text();
		var title = json.get("title").text();
		String author;
		String artistUrl;
		if (!json.get("artist").isNull() && json.get("artist").isMap()) {
			author = json.get("artist").get("name").get("display").text();
			artistUrl = "https://open.qobuz.com/artist/" + json.get("artist").get("id").text();
		} else {
			author = json.get("album").get("artist").get("name").text();
			artistUrl = "https://open.qobuz.com/artist/" + json.get("album").get("artist").get("id").text();
		}
		var artworkUrl = json.get("album").get("image").get("large").isNull() ? null : json.get("album").get("image").get("large").text();
		var length = json.get("duration").asLong(0) * 1000;
		var uri = "https://open.qobuz.com/track/" + identifier;
		var albumName = json.get("album").get("title").isNull() ? null : json.get("album").get("title").text();
		var albumUrl = json.get("album").get("id").isNull() ? null : "https://open.qobuz.com/album/" + json.get("album").get("id").text();
		String artistArtworkUrl = null;
		if (!json.get("album").get("artist").isNull() && !json.get("album").get("artist").get("image").isNull()) {
			artistArtworkUrl = json.get("album").get("artist").get("image").text();
		}

		var isrc = json.get("isrc").text();
		var info = new AudioTrackInfo(title, author, length, identifier, false, uri, artworkUrl, isrc);
		return new QobuzAudioTrack(info, albumName, albumUrl, artistUrl, artistArtworkUrl, this);
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(API_URL + "catalog/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=15&type=tracks");
		if (json == null || json.get("tracks").isNull() || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Qobuz Search: " + query, this.parseTracks(json.get("tracks").get("items")), null, true);
	}

	private AudioItem getAlbum(String id) throws IOException {
		var json = this.getJson(API_URL + "album/get?album_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "&limit=" + ALBUM_LOAD_LIMIT + "&offset=0");
		if (json == null || json.get("tracks").isNull() || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var title = json.get("title").text();
		var artworkUrl = json.get("image").get("large").text();
		var author = json.get("artist").get("name").text();
		var uri = "https://open.qobuz.com/album/" + id;
		var tracks = json.get("tracks").get("items");
		json.remove("tracks");
		var m = new ArrayList<AudioTrack>();
		for (var track : tracks.values()) {
			track.put("album", json);
			m.add(this.parseTrack(track));
		}
		int trackCount = tracks.values().size();
		return new ExtendedAudioPlaylist(title,
			m,
			ExtendedAudioPlaylist.Type.ALBUM,
			uri,
			artworkUrl,
			author,
			trackCount);
	}

	private AudioItem getTrack(String id) throws IOException {
		JsonBrowser json = this.getJson(API_URL + "track/get?track_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8));
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		return this.parseTrack(json);
	}

	private AudioItem getRecommendations(String id) throws IOException {
		var json = this.getJson(API_URL + "track/get?track_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8));
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		String artistId = json.get("performer").get("id").text();
		String jsonPayload = "{"
			+ "\"limit\": 50,"
			+ "\"listened_tracks_ids\": [" + id + "],"
			+ "\"track_to_analysed\": ["
			+ "    {"
			+ "        \"track_id\": " + id + ","
			+ "        \"artist_id\": " + artistId
			+ "    }"
			+ "]"
			+ "}";
		var request = new HttpPost(API_URL + "dynamic/suggest");
		request.setHeader("Accept", "application/json");
		request.setHeader("x-app-id", this.appId);
		request.setHeader("x-user-auth-token", this.userOauthToken);
		request.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));
		var recommendations = LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
		if (recommendations == null || recommendations.get("tracks").isNull() || recommendations.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(recommendations.get("tracks").get("items"));
		return new ExtendedAudioPlaylist("Qobuz Recommendations", tracks, ExtendedAudioPlaylist.Type.RECOMMENDATIONS, null, null, "Qobuz", tracks.size());
	}

	private AudioItem getTrackByISRC(String isrc) throws IOException {
		var json = this.getJson(API_URL + "catalog/search?query=" + URLEncoder.encode(isrc, StandardCharsets.UTF_8) + "&limit=15&type=tracks");
		if (json == null || json.get("tracks").isNull() || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return this.parseTrack(json.get("tracks").get("items").values().get(0));
	}

	private AudioItem getPlaylist(String id) throws IOException {
		var json = this.getJson(API_URL + "playlist/get?playlist_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "&limit=" + PLAYLIST_LOAD_LIMIT + "&offset=0" + "&extra=tracks");
		if (json == null || json.get("tracks").isNull() || json.get("tracks").get("items").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		String artworkUrl = null;
		if (!json.get("images300").values().isEmpty()) {
			artworkUrl = json.get("images300").values().get(0).text();
		}
		var author = json.get("owner").get("name").text();
		var title = json.get("name").text();
		var tracks = json.get("tracks").get("items");
		var url = json.get("url").text();
		int trackCount = (tracks != null) ? tracks.values().size() : 0;
		return new ExtendedAudioPlaylist(title,
			this.parseTracks(tracks),
			ExtendedAudioPlaylist.Type.PLAYLIST,
			url,
			artworkUrl,
			author,
			trackCount);
	}

	private AudioItem getArtist(String id) throws IOException {
		var json = this.getJson(API_URL + "artist/page?artist_id=" + URLEncoder.encode(id, StandardCharsets.UTF_8));
		if (json == null || json.get("top_tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracksJson = json.get("top_tracks");
		if (tracksJson == null || tracksJson.isNull()) {
			return AudioReference.NO_TRACK;
		}
		int trackCount = tracksJson.values().size();
		String artworkUrl = null;
		if (!json.get("images").get("potrait").isNull()
			&& !json.get("images").get("potrait").get("hash").text().isEmpty()) {
			artworkUrl = "https://static.qobuz.com/images/artists/covers/large/"
				+ json.get("images").get("potrait").get("hash").text() + ".jpg";
		}
		var uri = "https://open.qobuz.com/artist/" + id;
		var author = json.get("name").get("display").text();
		return new ExtendedAudioPlaylist(author + "'s Top Tracks", this.parseTracks(tracksJson), ExtendedAudioPlaylist.Type.ARTIST, uri, artworkUrl, author, trackCount);
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