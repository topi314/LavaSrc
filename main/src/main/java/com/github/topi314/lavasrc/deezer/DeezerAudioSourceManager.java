package com.github.topi314.lavasrc.deezer;

import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeezerAudioSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable, AudioSearchManager, AudioLyricsManager {
	private static final byte[] decryptionKeyHash = new byte[] {
		52, 76, 41, -118, 120, -123, 48, 72, -58, 74, 16, 75, 82, 101, -70, -33, 15, -66, 111, -38, -80, 71, 103, 11, -75, -120, -101, -9, 66, -53, -38, -16
	};

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?deezer\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>track|album|playlist|artist)/(?<identifier>[0-9]+)");
	public static final String SEARCH_PREFIX = "dzsearch:";
	public static final String ISRC_PREFIX = "dzisrc:";
	public static final String PREVIEW_PREFIX = "dzprev:";
	public static final String RECOMMENDATIONS_PREFIX = "dzrec:";
	public static final String RECOMMENDATIONS_ARTIST_PREFIX = "artist=";
	public static final String RECOMMENDATIONS_TRACK_PREFIX = "track=";
	public static final long PREVIEW_LENGTH = 30000;
	public static final String SHARE_URL = "https://deezer.page.link/";
	public static final String PUBLIC_API_BASE = "https://api.deezer.com/2.0";
	public static final String PRIVATE_API_BASE = "https://www.deezer.com/ajax/gw-light.php";
	public static final String MEDIA_BASE = "https://media.deezer.com/v1";
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK, AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.ARTIST);
	private static final Logger log = LoggerFactory.getLogger(DeezerAudioSourceManager.class);

	private final String masterDecryptionKey;
	private final DeezerTokenTracker tokenTracker;
	private final HttpInterfaceManager httpInterfaceManager;
	private DeezerAudioTrack.TrackFormat[] formats;

	public DeezerAudioSourceManager(String masterDecryptionKey) {
		this(masterDecryptionKey, null);
	}

	public DeezerAudioSourceManager(String masterDecryptionKey, @Nullable String arl) {
		this(masterDecryptionKey, arl, null);
	}

	public DeezerAudioSourceManager(String masterDecryptionKey, @Nullable String arl, @Nullable DeezerAudioTrack.TrackFormat[] formats) {
		if (masterDecryptionKey == null || masterDecryptionKey.isEmpty()) {
			throw new IllegalArgumentException("Deezer master key must be set");
		}

		if (!validateDecryptionKey(masterDecryptionKey)) {
			log.warn("Deezer master decryption key is possibly invalid, playback may not work!");
		}

		this.masterDecryptionKey = masterDecryptionKey;
		this.tokenTracker = new DeezerTokenTracker(this, arl);
		this.formats = formats != null && formats.length > 0 ? formats : DeezerAudioTrack.TrackFormat.DEFAULT_FORMATS;
		this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
	}

	public boolean validateDecryptionKey(String masterDecryptionKey) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			messageDigest.update(masterDecryptionKey.getBytes(StandardCharsets.UTF_8));
			return Arrays.equals(messageDigest.digest(), decryptionKeyHash);
		} catch (NoSuchAlgorithmException e) {
			return false;
		}
	}

	static void checkResponse(JsonBrowser json, String message) throws IllegalStateException {
		if (json == null) {
			throw new IllegalStateException(message + "No response");
		}
		var error = json.get("error").safeText();
		if (!error.equals("{}") && !error.equals("[]") && !error.equals("null") && !error.isEmpty()) {
			throw new IllegalStateException(message + ": " + error);
		}


		var errors = json.get("errors").safeText();
		if (!errors.equals("[]") && !errors.isEmpty()) {
			throw new IllegalStateException(message + ": " + errors);
		}
	}

	public void setArl(String arl) {
		this.tokenTracker.setArl(arl);
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "deezer";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new DeezerAudioTrack(trackInfo,
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
	@Nullable
	public AudioLyrics loadLyrics(@NotNull AudioTrack audioTrack) {
		var deezerTackId = "";
		if (audioTrack instanceof DeezerAudioTrack) {
			deezerTackId = audioTrack.getIdentifier();
		}

		if (deezerTackId.isEmpty()) {
			AudioItem item = AudioReference.NO_TRACK;
			try {
				if (audioTrack.getInfo().isrc != null && !audioTrack.getInfo().isrc.isEmpty()) {
					item = this.getTrackByISRC(audioTrack.getInfo().isrc, false);
				}
				if (item == AudioReference.NO_TRACK) {
					item = this.getSearch(String.format("%s %s", audioTrack.getInfo().title, audioTrack.getInfo().author), false);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (item == AudioReference.NO_TRACK) {
				return null;
			}
			if (item instanceof AudioTrack) {
				deezerTackId = ((AudioTrack) item).getIdentifier();
			} else if (item instanceof AudioPlaylist) {
				var playlist = (AudioPlaylist) item;
				if (!playlist.getTracks().isEmpty()) {
					deezerTackId = playlist.getTracks().get(0).getIdentifier();
				}
			}
		}

		try {
			return this.getLyrics(deezerTackId);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	@Nullable
	public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
		try {
			if (query.startsWith(SEARCH_PREFIX)) {
				return this.getAutocomplete(query.substring(SEARCH_PREFIX.length()), types);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
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
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()), preview);
			}

			if (identifier.startsWith(ISRC_PREFIX)) {
				return this.getTrackByISRC(identifier.substring(ISRC_PREFIX.length()), preview);
			}

			if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				return this.getRecommendations(identifier.substring(RECOMMENDATIONS_PREFIX.length()), preview);
			}

			// If the identifier is a share URL, we need to follow the redirect to find out the real url behind it
			if (identifier.startsWith(SHARE_URL)) {
				var request = new HttpGet(identifier);
				request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
				try (var response = this.httpInterfaceManager.getInterface().execute(request)) {
					if (response.getStatusLine().getStatusCode() == 302) {
						var location = response.getFirstHeader("Location").getValue();
						if (location.startsWith("https://www.deezer.com/")) {
							return this.loadItem(location, preview);
						}
					}
					return null;
				}
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

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview) {
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.get("data").values()) {
			if (!track.get("type").text().equals("track")) {
				continue;
			}
			if (!track.get("readable").asBoolean(false)) {
				log.warn("Skipping track {} by {} because it is not readable. Available countries: {}", track.get("title").text(), track.get("artist").get("name").safeText(), track.get("available_countries").text());
				continue;
			}
			tracks.add(this.parseTrack(track, preview));
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json, boolean preview) {
		if (!json.get("readable").asBoolean(false)) {
			throw new FriendlyException("This track is not readable. Available countries: " + json.get("available_countries").text(),
				FriendlyException.Severity.COMMON, null);
		}
		var id = json.get("id").text();
		return new DeezerAudioTrack(
			new AudioTrackInfo(
				json.get("title").safeText(),
				json.get("artist").get("name").safeText(),
				preview ? PREVIEW_LENGTH : json.get("duration").asLong(0) * 1000,
				id,
				false,
				"https://deezer.com/track/" + id,
				json.get("album").get("cover_xl").text(),
				json.get("isrc").text()
			),
			json.get("album").get("title").text(),
			"https://www.deezer.com/album/" + json.get("album").get("id").text(),
			"https://www.deezer.com/artist/" + json.get("artist").get("id").text(),
			json.get("artist").get("picture_xl").text(),
			json.get("preview").text(),
			preview,
			this
		);
	}

	private AudioSearchResult getAutocomplete(String query, Set<AudioSearchResult.Type> types) throws IOException {
		if (types.isEmpty()) {
			types = SEARCH_TYPES;
		}
		var json = this.getJson(PUBLIC_API_BASE + "/search/autocomplete?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
		if (json == null) {
			return AudioSearchResult.EMPTY;
		}

		var albums = new ArrayList<AudioPlaylist>();
		if (types.contains(AudioSearchResult.Type.ALBUM)) {
			for (var album : json.get("albums").get("data").values()) {
				albums.add(new DeezerAudioPlaylist(
					album.get("title").safeText(),
					Collections.emptyList(),
					DeezerAudioPlaylist.Type.ALBUM,
					album.get("link").text(),
					album.get("cover_xl").text(),
					album.get("artist").get("name").text(),
					(int) album.get("nb_tracks").asLong(0)
				));
			}
		}

		var artists = new ArrayList<AudioPlaylist>();
		if (types.contains(AudioSearchResult.Type.ARTIST)) {
			for (var artist : json.get("artists").get("data").values()) {
				artists.add(new DeezerAudioPlaylist(
					artist.get("name").safeText() + "'s Top Tracks",
					Collections.emptyList(),
					DeezerAudioPlaylist.Type.ARTIST,
					artist.get("link").text(),
					artist.get("picture_xl").text(),
					artist.get("name").text(),
					null
				));
			}
		}

		var playlists = new ArrayList<AudioPlaylist>();
		if (types.contains(AudioSearchResult.Type.PLAYLIST)) {
			for (var playlist : json.get("playlists").get("data").values()) {
				playlists.add(new DeezerAudioPlaylist(
					playlist.get("title").safeText(),
					Collections.emptyList(),
					DeezerAudioPlaylist.Type.PLAYLIST,
					playlist.get("link").text(),
					playlist.get("picture_xl").text(),
					playlist.get("creator").get("name").text(),
					(int) playlist.get("nb_tracks").asLong(0)
				));
			}
		}

		var tracks = new ArrayList<AudioTrack>();
		if (types.contains(AudioSearchResult.Type.TRACK)) {
			tracks.addAll(this.parseTracks(json.get("tracks"), false));
		}

		return new BasicAudioSearchResult(tracks, albums, artists, playlists, new ArrayList<>());
	}

	private AudioItem getTrackByISRC(String isrc, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/track/isrc:" + URLEncoder.encode(isrc, StandardCharsets.UTF_8));
		if (json == null || json.get("id").isNull()) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json, preview);
	}

	private AudioTrack parseRecommendationTrack(JsonBrowser json, boolean preview) {
		var id = json.get("SNG_ID").text();
		String previewUrl = null;
		if (!json.get("MEDIA").values().isEmpty()) {
			previewUrl = json.get("MEDIA").values().get(0).get("HREF").text();
		}
		return new DeezerAudioTrack(
			new AudioTrackInfo(json.get("SNG_TITLE").text(),
				json.get("ART_NAME").text(),
				preview ? PREVIEW_LENGTH : json.get("DURATION").asLong(0) * 1000,
				id,
				false,
				"https://deezer.com/track/" + id,
				"https://cdn-images.dzcdn.net/images/cover/" + json.get("ALB_PICTURE").text() + "/1000x1000-000000-80-0-0.jpg", !json.get("ISRC").isNull() ? json.get("ISRC").text() : null),
			json.get("ALB_TITLE").text(),
			"https://www.deezer.com/album/" + json.get("ALB_ID").text(),
			"https://www.deezer.com/artist/" + json.get("ART_ID").text(),
			"https://cdn-images.dzcdn.net/images/cover/" + json.get("ARTISTS").values().get(0).get("ART_PICTURE").text() + "/1000x1000-000000-80-0-0.jpg",
			previewUrl,
			preview,
			this);
	}

	public AudioLyrics getLyrics(String id) throws IOException {
		var tokens = this.tokenTracker.getTokens();

		var json = this.getJson(PRIVATE_API_BASE + "?method=song.getLyrics&api_version=1.0&api_token=" + tokens.api + "&sng_id=" + id);
		if (json == null || json.get("results").values().isEmpty()) {
			return null;
		}

		var results = json.get("results");
		var lyricsText = results.get("LYRICS_TEXT").text();
		var lyrics = new ArrayList<AudioLyrics.Line>();
		for (var line : results.get("LYRICS_SYNC_JSON").values()) {
			lyrics.add(new BasicAudioLyrics.BasicLine(
				Duration.ofMillis(line.get("milliseconds").asLong(0)),
				Duration.ofMillis(line.get("duration").asLong(0)),
				line.get("line").text()
			));
		}

		return new BasicAudioLyrics("deezer", "LyricFind", lyricsText, lyrics);
	}

	private AudioItem getRecommendations(String query, boolean preview) throws IOException {
		var tokens = this.tokenTracker.getTokens();

		String method;
		String payload;
		if (query.startsWith(RECOMMENDATIONS_ARTIST_PREFIX)) {
			method = "song.getSmartRadio";
			payload = String.format("{\"art_id\": %s}", query.substring(RECOMMENDATIONS_ARTIST_PREFIX.length()));
		} else {
			method = "song.getSearchTrackMix";
			payload = String.format("{\"sng_id\": %s, \"start_with_input_track\": \"true\"}", query.startsWith(RECOMMENDATIONS_TRACK_PREFIX) ? query.substring(RECOMMENDATIONS_TRACK_PREFIX.length()) : query);
		}

		var request = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + String.format("?method=%s&input=3&api_version=1.0&api_token=%s", method, tokens.api));
		request.setHeader("Cookie", "sid=" + tokens.sessionId + "; dzr_uniq_id=" + tokens.dzrUniqId);
		request.setHeader("Content-Type", "application/json");
		request.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));

		var result = LavaSrcTools.fetchResponseAsJson(this.getHttpInterface(), request);
		checkResponse(result, "Failed to get recommendations");

		if (result.get("results").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = result.get("results").get("data")
			.values()
			.stream()
			.map(value -> this.parseRecommendationTrack(value, preview))
			.collect(Collectors.toList());

		return new DeezerAudioPlaylist("Deezer Recommendations", tracks, DeezerAudioPlaylist.Type.RECOMMENDATIONS, null, null, null, tracks.size());
	}

	private AudioItem getSearch(String query, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
		if (json == null || json.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Deezer Search: " + query, this.parseTracks(json, preview), null, true);
	}

	private AudioItem getAlbum(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/album/" + id);
		if (json == null || json.get("tracks").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = json.get("cover_xl").text();
		var author = json.get("contributors").values().get(0).get("name").text();

		var tracks = this.getJson(PUBLIC_API_BASE + "/album/" + id + "/tracks?limit=10000");

		for (var track : tracks.get("data").values()) {
			track.get("artist").put("picture_xl", json.get("artist").get("picture_xl"));
		}

		return new DeezerAudioPlaylist(json.get("title").text(),
			this.parseTracks(tracks, preview),
			DeezerAudioPlaylist.Type.ALBUM,
			json.get("link").text(),
			artworkUrl,
			author,
			(int) json.get("nb_tracks").asLong(0));
	}

	private AudioItem getTrack(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/track/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json, preview);
	}

	private AudioItem getPlaylist(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/playlist/" + id);
		if (json == null || json.get("tracks").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = json.get("picture_xl").text();
		var author = json.get("creator").get("name").text();

		// This endpoint returns tracks with ISRC, unlike the other REST call
		var tracks = this.getJson(PUBLIC_API_BASE + "/playlist/" + id + "/tracks?limit=10000");

		return new DeezerAudioPlaylist(json.get("title").text(),
			this.parseTracks(tracks, preview),
			DeezerAudioPlaylist.Type.PLAYLIST,
			json.get("link").text(),
			artworkUrl,
			author,
			(int) json.get("nb_tracks").asLong(0));
	}

	private AudioItem getArtist(String id, boolean preview) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/artist/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracksJson = this.getJson(PUBLIC_API_BASE + "/artist/" + id + "/top?limit=50");
		if (tracksJson == null || tracksJson.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		for (var track : tracksJson.get("data").values()) {
			track.get("artist").put("picture_xl", json.get("picture_xl"));
		}

		var artworkUrl = json.get("picture_xl").text();
		var author = json.get("name").text();
		var deezerTracks = this.parseTracks(tracksJson, preview);
		return new DeezerAudioPlaylist(author + "'s Top Tracks", deezerTracks, DeezerAudioPlaylist.Type.ARTIST, json.get("link").text(), artworkUrl, author, deezerTracks.size());
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

	public String getMasterDecryptionKey() {
		return this.masterDecryptionKey;
	}

	public DeezerAudioTrack.TrackFormat[] getFormats() {
		return this.formats;
	}

	public void setFormats(DeezerAudioTrack.TrackFormat[] formats) {
		if (formats.length == 0) {
			throw new IllegalArgumentException("Deezer track formats must not be empty");
		}
		this.formats = formats;
	}

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}

	public DeezerTokenTracker getTokenTracker() {
		return this.tokenTracker;
	}
}
