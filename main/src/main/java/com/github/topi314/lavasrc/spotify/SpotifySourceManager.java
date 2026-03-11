package com.github.topi314.lavasrc.spotify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.github.topi314.lavasrc.RateLimitException;
import com.github.topi314.lavasrc.SpotifyWebApiFallbackException;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpotifySourceManager extends MirroringAudioSourceManager implements HttpConfigurable, AudioSearchManager, AudioLyricsManager {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)(www\\.)?open\\.spotify\\.com/((?<region>[a-zA-Z-]+)/)?(user/(?<user>[a-zA-Z0-9-_]+)/)?(?<type>track|album|playlist|artist)/(?<identifier>[a-zA-Z0-9-_]+)");
	public static final Pattern RADIO_MIX_QUERY_PATTERN = Pattern.compile("mix:(?<seedType>album|artist|track|isrc):(?<seed>[a-zA-Z0-9-_]+)");
	public static final String SEARCH_PREFIX = "spsearch:";
	public static final String RECOMMENDATIONS_PREFIX = "sprec:";
	public static final String PREVIEW_PREFIX = "spprev:";
	public static final long PREVIEW_LENGTH = 30000;
	public static final String SHARE_URL = "https://spotify.link/";
	public static final int PLAYLIST_MAX_PAGE_ITEMS = 100;
	public static final int ALBUM_MAX_PAGE_ITEMS = 50;
	public static final String API_BASE = "https://api.spotify.com/v1/";
	public static final String CLIENT_API_BASE = "https://spclient.wg.spotify.com/";
	private static final String TRACK_METADATA_ENDPOINT_PREFIX = CLIENT_API_BASE + "metadata/4/track/";
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.178 Spotify/1.2.65.255 Safari/537.36";
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.ARTIST, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.TRACK);
	private static final Logger log = LoggerFactory.getLogger(SpotifySourceManager.class);
	private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	private final SpotifyTokenTracker tokenTracker;
	private final SpotifyPartnerApiClient partnerApiClient;
	private final String countryCode;
	private int playlistPageLimit = 6;
	private int albumPageLimit = 6;
	private boolean localFiles;
	private boolean resolveArtistsInSearch = true;
	private boolean preferAnonymousToken = false;

	private static boolean isNullOrBlank(@Nullable String value) {
		return value == null || value.trim().isEmpty();
	}

	/**
	 * Converts Spotify base62 id to 16-byte hex gid used by some internal Spotify APIs.
	 */
	static String spotifyIdToHex(@NotNull String base62Id) {
		final String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		java.math.BigInteger value = java.math.BigInteger.ZERO;
		java.math.BigInteger base = java.math.BigInteger.valueOf(62);
		for (int i = 0; i < base62Id.length(); i++) {
			int idx = alphabet.indexOf(base62Id.charAt(i));
			if (idx < 0) {
				throw new IllegalArgumentException("Invalid base62 char in Spotify id: " + base62Id.charAt(i));
			}
			value = value.multiply(base).add(java.math.BigInteger.valueOf(idx));
		}
		String hex = value.toString(16);
		// Spotify gids are 16 bytes => 32 hex chars
		if (hex.length() > 32) {
			// Keep the least significant 16 bytes.
			hex = hex.substring(hex.length() - 32);
		}
		return "0".repeat(32 - hex.length()) + hex;
	}

	@Nullable
	static String parseIsrcFromSpClientMetadata(@Nullable JsonBrowser json) {
		if (json == null) {
			return null;
		}
		JsonBrowser externalIds = json.get("external_id");
		if (externalIds == null || !externalIds.isList()) {
			return null;
		}
		for (JsonBrowser item : externalIds.values()) {
			if (item == null || item.isNull()) {
				continue;
			}
			String type = item.get("type").text();
			if ("isrc".equalsIgnoreCase(type)) {
				String id = item.get("id").text();
				return isNullOrBlank(id) ? null : id;
			}
		}
		return null;
	}

	@Nullable
	private String fetchIsrcViaSpClientMetadata(@NotNull String trackId) {
		if (isNullOrBlank(trackId)) {
			return null;
		}

		String gid;
		try {
			gid = spotifyIdToHex(trackId);
		} catch (IllegalArgumentException e) {
			log.debug("Failed to convert Spotify id to hex gid: {}", e.getMessage());
			return null;
		}

		var url = TRACK_METADATA_ENDPOINT_PREFIX + gid + "?market=from_token";
		var request = new HttpGet(url);
		request.setHeader("User-Agent", USER_AGENT);
		request.setHeader("Accept", "application/json");
		request.setHeader("Content-Type", "application/json");
		try {
			request.setHeader("Authorization", "Bearer " + this.tokenTracker.getAnonymousAccessToken());
		} catch (IOException e) {
			log.debug("Failed to get anonymous Spotify token for metadata isrc fallback: {}", e.getMessage());
			return null;
		}

		try {
			var json = LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
			return parseIsrcFromSpClientMetadata(json);
		} catch (Exception e) {
			log.debug("Failed to fetch ISRC via spclient metadata: {}", e.getMessage());
			return null;
		}
	}

	public SpotifySourceManager(String[] providers, String clientId, String clientSecret, String countryCode,
	                            Function<Void, AudioPlayerManager> audioPlayerManager) {
		this(clientId, clientSecret, null, countryCode, audioPlayerManager,
			new DefaultMirroringAudioTrackResolver(providers));
	}

	public SpotifySourceManager(String clientId, String clientSecret, String countryCode,
	                            AudioPlayerManager audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		this(clientId, clientSecret, null, countryCode, unused -> audioPlayerManager, mirroringAudioTrackResolver);
	}

	public SpotifySourceManager(String clientId, String clientSecret, String countryCode,
	                            Function<Void, AudioPlayerManager> audioPlayerManager,
	                            MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		this(clientId, clientSecret, null, countryCode, audioPlayerManager, mirroringAudioTrackResolver);
	}

	public SpotifySourceManager(String clientId, String clientSecret, String spDc, String countryCode,
	                            Function<Void, AudioPlayerManager> audioPlayerManager,
	                            MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		this(clientId, clientSecret, false, spDc, countryCode, audioPlayerManager, mirroringAudioTrackResolver);
	}

	public SpotifySourceManager(String clientId, String clientSecret, boolean preferAnonymousToken, String spDc,
	                            String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager,
	                            MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		super(audioPlayerManager, mirroringAudioTrackResolver);

		this.tokenTracker = new SpotifyTokenTracker(this, clientId, clientSecret, spDc);
		this.partnerApiClient = new SpotifyPartnerApiClient(tokenTracker, httpInterfaceManager.getInterface());

		if (countryCode == null || countryCode.isEmpty()) {
			countryCode = "US";
		}
		this.countryCode = countryCode;
		this.preferAnonymousToken = preferAnonymousToken;
	}

	public SpotifySourceManager(String clientId, String clientSecret, boolean preferAnonymousToken,
	                            String customTokenEndpoint, String spDc, String countryCode,
	                            Function<Void, AudioPlayerManager> audioPlayerManager,
	                            MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		super(audioPlayerManager, mirroringAudioTrackResolver);

		this.tokenTracker = new SpotifyTokenTracker(this, clientId, clientSecret, spDc, customTokenEndpoint);
		this.partnerApiClient = new SpotifyPartnerApiClient(tokenTracker, httpInterfaceManager.getInterface());

		if (countryCode == null || countryCode.isEmpty()) {
			countryCode = "US";
		}
		this.countryCode = countryCode;
		this.preferAnonymousToken = preferAnonymousToken;
	}

	public void setPlaylistPageLimit(int playlistPageLimit) {
		this.playlistPageLimit = playlistPageLimit;
	}

	public void setAlbumPageLimit(int albumPageLimit) {
		this.albumPageLimit = albumPageLimit;
	}

	public void setLocalFiles(boolean localFiles) {
		this.localFiles = localFiles;
	}

	public void setResolveArtistsInSearch(boolean resolveArtistsInSearch) {
		this.resolveArtistsInSearch = resolveArtistsInSearch;
	}

	public void setClientIDSecret(String clientId, String clientSecret) {
		this.tokenTracker.setClientIDS(clientId, clientSecret);
	}

	public void setSpDc(String spDc) {
		this.tokenTracker.setSpDc(spDc);
	}

	public void setPreferAnonymousToken(boolean preferAnonymousToken) {
		this.preferAnonymousToken = preferAnonymousToken;
	}

	public void setCustomTokenEndpoint(String customTokenEndpoint) {
		this.tokenTracker.setCustomTokenEndpoint(customTokenEndpoint);
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "spotify";
	}

	@Override
	@Nullable
	public AudioLyrics loadLyrics(@NotNull AudioTrack audioTrack) {
		var spotifyTackId = "";
		if (audioTrack instanceof SpotifyAudioTrack) {
			spotifyTackId = audioTrack.getIdentifier();
		}

		if (spotifyTackId.isEmpty()) {
			AudioItem item = AudioReference.NO_TRACK;
			try {
				if (audioTrack.getInfo().isrc != null && !audioTrack.getInfo().isrc.isEmpty()) {
					item = this.getSearch("isrc:" + audioTrack.getInfo().isrc, false);
				}
				if (item == AudioReference.NO_TRACK) {
					item = this.getSearch(
						String.format("%s %s", audioTrack.getInfo().title, audioTrack.getInfo().author), false);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (item == AudioReference.NO_TRACK) {
				return null;
			}
			if (item instanceof AudioTrack) {
				spotifyTackId = ((AudioTrack) item).getIdentifier();
			} else if (item instanceof AudioPlaylist) {
				var playlist = (AudioPlaylist) item;
				if (!playlist.getTracks().isEmpty()) {
					spotifyTackId = playlist.getTracks().get(0).getIdentifier();
				}
			}
		}

		try {
			return this.getLyrics(spotifyTackId);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public AudioLyrics getLyrics(String id) throws IOException {
		if (!this.tokenTracker.hasValidAccountCredentials()) {
			throw new IllegalArgumentException("Spotify spDc must be set");
		}

		var request = new HttpGet(CLIENT_API_BASE + "color-lyrics/v2/track/" + id + "?format=json&vocalRemoval=false");
		request.setHeader("User-Agent", USER_AGENT);
		request.setHeader("App-Platform", "WebPlayer");
		request.setHeader("Authorization", "Bearer " + this.tokenTracker.getAccountAccessToken());
		var json = LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
		if (json == null) {
			return null;
		}

		var lyrics = new ArrayList<AudioLyrics.Line>();
		for (var line : json.get("lyrics").get("lines").values()) {
			lyrics.add(new BasicAudioLyrics.BasicLine(
				Duration.ofMillis(line.get("startTimeMs").asLong(0)),
				null,
				line.get("words").text()));
		}

		return new BasicAudioLyrics("spotify",
			json.get("lyrics").get("providerDisplayName").textOrDefault("MusixMatch"), null, lyrics);
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
			this);
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
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()).trim(), preview);
			}

			if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				return this.getRecommendations(identifier.substring(RECOMMENDATIONS_PREFIX.length()).trim(), preview);
			}

			if (identifier.startsWith(SHARE_URL)) {
				var request = new HttpHead(identifier);
				request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
				try (var response = this.httpInterfaceManager.getInterface().execute(request)) {
					if (response.getStatusLine().getStatusCode() == 307) {
						var location = response.getFirstHeader("Location").getValue();
						if (location.startsWith("https://open.spotify.com/")) {
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

	public JsonBrowser getJson(String uri, boolean anonymous, boolean preferAnonymous) throws IOException {
		var accessToken = anonymous
			? this.tokenTracker.getAnonymousAccessToken()
			: this.tokenTracker.getAccessToken(preferAnonymous);
		return this.fetchSpotifyApiResponse(uri, accessToken);
	}

	private JsonBrowser fetchSpotifyApiResponse(String uri, String accessToken) throws IOException {
		var request = new HttpGet(uri);
		request.addHeader("Authorization", "Bearer " + accessToken);

		try (CloseableHttpResponse response = this.httpInterfaceManager.getInterface().execute(request)) {
			int statusCode = response.getStatusLine().getStatusCode();
			String data = null;
			if (response.getEntity() != null) {
				data = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			}

			if (statusCode == HttpStatus.SC_NOT_FOUND) {
				log.error("Server responded with not found to '{}': {}", request.getURI(), data);
				return null;
			} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
				log.error("Server responded with not content to '{}'", request.getURI());
				return null;
			} else if (statusCode == HttpStatus.SC_TOO_MANY_REQUESTS) {
				Long retryAfterSeconds = parseRetryAfterSeconds(response.getFirstHeader("Retry-After"));
				log.error("Server responded with an error to '{}': {}", request.getURI(), data);
				throw new RateLimitException(
					"Server responded with an error.",
					statusCode,
					retryAfterSeconds,
					data,
					new IllegalStateException("Response code from channel info is " + statusCode));
			} else if (SpotifyWebApiFallbackException.shouldFallbackToPartnerApi(statusCode, data)) {
				log.error("Server responded with an error to '{}': {}", request.getURI(), data);
				throw new SpotifyWebApiFallbackException(statusCode, data);
			} else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
				log.error("Server responded with an error to '{}': {}", request.getURI(), data);
				throw new FriendlyException("Server responded with an error.", FriendlyException.Severity.SUSPICIOUS,
					new IllegalStateException("Response code from channel info is " + statusCode));
			}

			log.debug("Response from '{}' was successful: {}", request.getURI(), data);
			return data == null ? null : JsonBrowser.parse(data);
		}
	}

	@Nullable
	private static Long parseRetryAfterSeconds(@Nullable Header retryAfter) {
		if (retryAfter == null) {
			return null;
		}

		try {
			return Long.parseLong(retryAfter.getValue().trim());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private AudioSearchResult getAutocomplete(String query, Set<AudioSearchResult.Type> types) throws IOException {
		if (types.isEmpty()) {
			types = SEARCH_TYPES;
		}
		var url = API_BASE + "search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type="
			+ types.stream().map(AudioSearchResult.Type::getName).collect(Collectors.joining(","));
		var json = this.getJson(url, false, false);
		if (json == null) {
			return AudioSearchResult.EMPTY;
		}

		var albums = new ArrayList<AudioPlaylist>();
		for (var album : json.get("albums").get("items").values()) {
			albums.add(new SpotifyAudioPlaylist(
				album.get("name").safeText(),
				Collections.emptyList(),
				ExtendedAudioPlaylist.Type.ALBUM,
				album.get("external_urls").get("spotify").text(),
				album.get("images").index(0).get("url").text(),
				album.get("artists").index(0).get("name").text(),
				(int) album.get("total_tracks").asLong(0)));
		}

		var artists = new ArrayList<AudioPlaylist>();
		for (var artist : json.get("artists").get("items").values()) {
			artists.add(new SpotifyAudioPlaylist(
				artist.get("name").safeText() + "'s Top Tracks",
				Collections.emptyList(),
				ExtendedAudioPlaylist.Type.ARTIST,
				artist.get("external_urls").get("spotify").text(),
				artist.get("images").index(0).get("url").text(),
				artist.get("name").text(),
				null));
		}

		var playlists = new ArrayList<AudioPlaylist>();
		for (var playlist : json.get("playlists").get("items").values()) {
			playlists.add(new SpotifyAudioPlaylist(
				playlist.get("name").safeText(),
				Collections.emptyList(),
				ExtendedAudioPlaylist.Type.PLAYLIST,
				playlist.get("external_urls").get("spotify").text(),
				playlist.get("images").index(0).get("url").text(),
				playlist.get("owner").get("display_name").text(),
				(int) playlist.get("tracks").get("total").asLong(0)));
		}

		var tracks = this.parseTrackItems(json.get("tracks"), false);

		return new BasicAudioSearchResult(tracks, albums, artists, playlists, new ArrayList<>());
	}

	public AudioItem getSearch(String query, boolean preview) throws IOException {
		try {
			JsonBrowser json = this.partnerApiClient.search(query, 0, 10, true, false, true, false, 5);
			if (json == null || json.get("data").get("searchV2").get("tracksV2").get("items").values().isEmpty()) {
				return AudioReference.NO_TRACK;
			}

			List<AudioTrack> tracks = new ArrayList<>();
			for (var item : json.get("data").get("searchV2").get("tracksV2").get("items").values()) {
				JsonBrowser trackData = item.get("item").get("data");
				tracks.add(this.parsePartnerTrack(trackData, preview));
			}

			return new BasicAudioPlaylist("Spotify Search: " + query, tracks, null, true);
		} catch (JsonProcessingException e) {
			throw new IOException(e);
		}
	}

	public AudioItem getRecommendations(String seedTrackId, boolean preview) throws IOException {
		try {
			JsonBrowser json = this.partnerApiClient.getRecommendations("spotify:track:" + seedTrackId);
			if (json == null) {
				return AudioReference.NO_TRACK;
			}

			JsonBrowser items = json.get("data").get("internalLinkRecommenderTrack").get("items");

			if (items.isNull()) {
				items = json.get("data").get("seoRecommendedTrack").get("items");
			}

			if (!items.isList() || items.values().isEmpty()) {
				return AudioReference.NO_TRACK;
			}

			List<AudioTrack> tracks = new ArrayList<>();

			for (var item : items.values()) {
				JsonBrowser trackData = item.get("content").get("data");
				if (trackData.isNull()) {
					trackData = item.get("data");
				}

				if (trackData.isNull()
					|| !"Track".equals(trackData.get("__typename").text())) {
					continue;
				}

				tracks.add(parsePartnerRecommendationTrack(trackData, preview));
			}

			if (tracks.isEmpty()) {
				return AudioReference.NO_TRACK;
			}

			return new SpotifyAudioPlaylist(
				"Spotify Recommendations",
				tracks,
				ExtendedAudioPlaylist.Type.RECOMMENDATIONS,
				null,
				null,
				null,
				tracks.size());
		} catch (JsonProcessingException e) {
			throw new IOException(e);
		}
	}

	private AudioTrack parsePartnerTrack(JsonBrowser track, boolean preview) {
		return parsePartnerTrack(track, preview, null);
	}

	private AudioTrack parsePartnerTrack(JsonBrowser track, boolean preview, String albumArtworkUrl) {
		String title = track.get("name").safeText();
		if (title == null || title.isEmpty()) {
			title = "Unknown Title";
		}

		long length = track.get("duration").get("totalMilliseconds").asLong(0);
		if (length == 0) {
			length = track.get("trackDuration").get("totalMilliseconds").asLong(0);
		}
		if (length == 0) {
			length = track.get("duration_ms").asLong(0);
		}

		String spotifyUri = track.get("uri").text();
		String identifier = "";
		if (spotifyUri != null && !spotifyUri.isEmpty()) {
			identifier = spotifyUri.replace("spotify:track:", "");
		} else {
			identifier = track.get("id").text();
		}

		String uri = "https://open.spotify.com/track/" + identifier;

		JsonBrowser artistsJson = track.get("artists").get("items");
		if (artistsJson == null || !artistsJson.isList() || artistsJson.values().isEmpty()) {
			artistsJson = track.get("firstArtist").get("items");
		}
		String author = "Unknown Artist";

		if (artistsJson != null && artistsJson.isList() && !artistsJson.values().isEmpty()) {
			author = artistsJson.values().stream()
				.map(a -> {
					String name = a.get("profile").get("name").text();
					return (name != null && !name.isEmpty()) ? name : null;
				})
				.filter(name -> name != null)
				.collect(Collectors.joining(", "));

			if (author == null || author.isEmpty()) {
				author = "Unknown Artist";
			}
		}

		JsonBrowser album = track.get("albumOfTrack");
		String albumName = "";
		String albumUrl = "";
		String artworkUrl = albumArtworkUrl;

		if (album != null && !album.isNull()) {
			albumName = album.get("name").safeText();
			if (albumName == null) {
				albumName = "";
			}

			String albumUri = album.get("uri").text();
			if (albumUri != null && !albumUri.isEmpty()) {
				String albumId = albumUri.replace("spotify:album:", "");
				albumUrl = "https://open.spotify.com/album/" + albumId;
			}
			if (artworkUrl == null) {
				JsonBrowser sources = album.get("coverArt").get("sources");
				if (sources != null && sources.isList() && !sources.values().isEmpty()) {
					int size = sources.values().size();
					artworkUrl = sources.values().get(size - 1).get("url").text();
				}
			}
		}

		String isrc = null;
		if (track.get("externalIds") != null && track.get("externalIds").get("isrc") != null) {
			isrc = track.get("externalIds").get("isrc").text();
		}
		if (isNullOrBlank(isrc) && !isNullOrBlank(identifier)) {
			isrc = fetchIsrcViaSpClientMetadata(identifier);
		}
		String previewUrl = null;
		if (track.get("previews") != null && track.get("previews").get("audioPreviews") != null && track.get("previews").get("audioPreviews").get("items") != null && track.get("previews").get("audioPreviews").get("items").values().size() > 0) {
			previewUrl = track.get("previews").get("audioPreviews").get("items").values().get(0).get("url").text();
		}
		String artistUrl = null;
		String artistArtworkUrl = null;
		if (track.get("artists") != null && track.get("artists").get("items") != null && track.get("artists").get("items").values().size() > 0) {
			JsonBrowser artist = track.get("artists").get("items").values().get(0);
			if (artist.get("uri") != null) {
				artistUrl = "https://open.spotify.com/artist/" + artist.get("uri").text().replace("spotify:artist:", "");
			}
			if (artist.get("profile") != null && artist.get("profile").get("name") != null) {
			}
			if (artist.get("visuals") != null && artist.get("visuals").get("avatarImage") != null && artist.get("visuals").get("avatarImage").get("sources") != null && artist.get("visuals").get("avatarImage").get("sources").values().size() > 0) {
				JsonBrowser best = artist.get("visuals").get("avatarImage").get("sources").values().get(0);
				for (JsonBrowser src : artist.get("visuals").get("avatarImage").get("sources").values()) {
					if (src.get("height").asLong(0) > best.get("height").asLong(0)) {
						best = src;
					}
				}
				artistArtworkUrl = best.get("url").text();
			}
		}
		String contentRating = null;
		if (track.get("contentRating") != null && track.get("contentRating").get("label") != null) {
			contentRating = track.get("contentRating").get("label").text();
		}
		String playcount = null;
		if (track.get("playcount") != null) {
			playcount = track.get("playcount").text();
		}
		Integer trackNumber = null;
		if (track.get("trackNumber") != null) {
			trackNumber = (int) track.get("trackNumber").asLong(0);
		}

		Map<String, Object> pluginInfo = new HashMap<>();
		pluginInfo.put("isrc", isrc);
		pluginInfo.put("previewUrl", previewUrl);
		pluginInfo.put("artistUrl", artistUrl);
		pluginInfo.put("artistArtworkUrl", artistArtworkUrl);
		pluginInfo.put("contentRating", contentRating);
		pluginInfo.put("playcount", playcount);
		pluginInfo.put("trackNumber", trackNumber);

		return new SpotifyAudioTrack(
			new AudioTrackInfo(
				title,
				author,
				preview ? PREVIEW_LENGTH : length,
				identifier,
				false,
				uri,
				artworkUrl,
				isrc),
			albumName,
			albumUrl,
			artistUrl,
			artistArtworkUrl,
			previewUrl,
			preview,
			this);
	}

	private AudioTrack parsePartnerRecommendationTrack(JsonBrowser track, boolean preview) {
		String title = track.get("name").safeText();
		if (title == null || title.isEmpty()) {
			title = "Unknown Title";
		}

		long length = track.get("duration").get("totalMilliseconds").asLong(0);
		if (length == 0) {
			length = track.get("trackDuration").get("totalMilliseconds").asLong(0);
		}
		if (length == 0) {
			length = track.get("duration_ms").asLong(0);
		}

		String spotifyUri = track.get("uri").text();
		String identifier = "";
		if (spotifyUri != null && !spotifyUri.isEmpty()) {
			identifier = spotifyUri.replace("spotify:track:", "");
		} else {
			identifier = track.get("id").text();
		}

		String uri = "https://open.spotify.com/track/" + identifier;

		JsonBrowser artistsJson = track.get("artists").get("items");
		String author = "Unknown Artist";

		if (artistsJson != null && artistsJson.isList() && !artistsJson.values().isEmpty()) {
			author = artistsJson.values().stream()
				.map(a -> {
					String name = a.get("profile").get("name").text();
					return (name != null && !name.isEmpty()) ? name : null;
				})
				.filter(name -> name != null)
				.collect(Collectors.joining(", "));

			if (author == null || author.isEmpty()) {
				author = "Unknown Artist";
			}
		}

		JsonBrowser album = track.get("albumOfTrack");
		String albumName = "";
		String albumUrl = "";
		String artworkUrl = null;

		if (album != null && !album.isNull()) {
			albumName = album.get("name").safeText();
			if (albumName == null) {
				albumName = "";
			}

			String albumUri = album.get("uri").text();
			if (albumUri != null && !albumUri.isEmpty()) {
				String albumId = albumUri.replace("spotify:album:", "");
				albumUrl = "https://open.spotify.com/album/" + albumId;
			}

			JsonBrowser sources = album.get("coverArt").get("sources");
			if (sources != null && sources.isList() && !sources.values().isEmpty()) {
				int size = sources.values().size();
				artworkUrl = sources.values().get(size - 1).get("url").text();
			}
		}

		return new SpotifyAudioTrack(
			new AudioTrackInfo(
				title,
				author,
				preview ? PREVIEW_LENGTH : length,
				identifier,
				false,
				uri,
				artworkUrl,
				null),
			albumName,
			albumUrl,
			null,
			null,
			null,
			preview,
			this);
	}

	public AudioItem getAlbum(String id, boolean preview) throws IOException {
		try {
			var json = this.getJson(API_BASE + "albums/" + id, false, this.preferAnonymousToken);
			if (json == null) {
				log.warn("Main API failed for album {}, trying partner API as fallback", id);
				return this.getPartnerAlbum(id, preview);
			}

			JsonBrowser artistJson;
			try {
				artistJson = this.getJson(API_BASE + "artists/" + json.get("artists").index(0).get("id").text(), false,
					this.preferAnonymousToken);
			} catch (RateLimitException | SpotifyWebApiFallbackException e) {
				log.warn("Spotify Web API unavailable while loading album artist {}, switching to partner API for album {}",
					json.get("artists").index(0).get("id").text(), id);
				return this.getPartnerAlbum(id, preview);
			}
			if (artistJson == null) {
				artistJson = JsonBrowser.newMap();
			}

			var tracks = new ArrayList<AudioTrack>();
			JsonBrowser page;
			var offset = 0;
			var pages = 0;
			do {
				try {
					page = this.getJson(
						API_BASE + "albums/" + id + "/tracks?limit=" + ALBUM_MAX_PAGE_ITEMS + "&offset=" + offset, false,
						this.preferAnonymousToken);
				} catch (RateLimitException | SpotifyWebApiFallbackException e) {
					log.warn("Spotify Web API unavailable while paging album tracks {}, switching to partner API", id);
					return this.getPartnerAlbum(id, preview);
				}
				offset += ALBUM_MAX_PAGE_ITEMS;

				JsonBrowser tracksPage;
				try {
					tracksPage = this.getJson(
						API_BASE + "tracks/?ids=" + page.get("items").values().stream()
							.map(track -> track.get("id").text()).collect(Collectors.joining(",")),
						false, this.preferAnonymousToken);
				} catch (RateLimitException | SpotifyWebApiFallbackException e) {
					log.warn("Spotify Web API unavailable while loading album track details {}, switching to partner API", id);
					return this.getPartnerAlbum(id, preview);
				}

				for (var track : tracksPage.get("tracks").values()) {
					var albumJson = JsonBrowser.newMap();
					albumJson.put("external_urls", json.get("external_urls"));
					albumJson.put("name", json.get("name"));
					albumJson.put("images", json.get("images"));
					track.put("album", albumJson);

					track.get("artists").index(0).put("images", artistJson.get("images"));
				}

				tracks.addAll(this.parseTracks(tracksPage, preview));
			} while (page.get("next").text() != null && ++pages < this.albumPageLimit);

			if (tracks.isEmpty()) {
				return AudioReference.NO_TRACK;
			}

			return new SpotifyAudioPlaylist(json.get("name").safeText(), tracks, ExtendedAudioPlaylist.Type.ALBUM,
				json.get("external_urls").get("spotify").text(), json.get("images").index(0).get("url").text(),
				json.get("artists").index(0).get("name").text(), (int) json.get("total_tracks").asLong(0));
		} catch (RateLimitException | SpotifyWebApiFallbackException e) {
			log.warn("Spotify Web API unavailable, switching to partner API for album {}", id);
			return this.getPartnerAlbum(id, preview);
		} catch (Exception e) {
			if (e instanceof IOException) throw (IOException) e;
			throw new IOException(e);
		}
	}

	private AudioItem getPartnerAlbum(String id, boolean preview) throws IOException {
		try {
			JsonBrowser json = this.partnerApiClient.getAlbum(id, 0, 50);
			if (json == null || json.get("data").get("albumUnion").isNull()) {
				return AudioReference.NO_TRACK;
			}
			JsonBrowser albumData = json.get("data").get("albumUnion");
			String albumName = albumData.get("name").text();
			String albumUrl = "https://open.spotify.com/album/" + id;
			String albumImage = null;
			if (albumData.get("images").get("items").isList() && !albumData.get("images").get("items").values().isEmpty()) {
				JsonBrowser imageItem = albumData.get("images").get("items").index(0);
				if (imageItem.get("sources").isList() && !imageItem.get("sources").values().isEmpty()) {
					albumImage = imageItem.get("sources").index(0).get("url").text();
				}
			}
			if (albumImage == null && albumData.get("coverArt").get("sources").isList() && !albumData.get("coverArt").get("sources").values().isEmpty()) {
				int size = albumData.get("coverArt").get("sources").values().size();
				albumImage = albumData.get("coverArt").get("sources").values().get(size - 1).get("url").text();
			}
			String albumArtist = null;
			if (albumData.get("artists").get("items").isList() && !albumData.get("artists").get("items").values().isEmpty()) {
				albumArtist = albumData.get("artists").get("items").index(0).get("profile").get("name").text();
			}
			List<AudioTrack> tracks = new ArrayList<>();
			if (albumData.get("tracksV2").get("items").isList() && !albumData.get("tracksV2").get("items").values().isEmpty()) {
				for (var item : albumData.get("tracksV2").get("items").values()) {
					var track = item.get("track");
					if (!track.isNull() && track.get("uri").text() != null && track.get("playability").get("playable").asBoolean(true)) {
						tracks.add(this.parsePartnerTrack(track, preview, albumImage));
					}
				}
			} else if (albumData.get("tracks").get("items").isList() && !albumData.get("tracks").get("items").values().isEmpty()) {
				for (var item : albumData.get("tracks").get("items").values()) {
					var track = item.get("track");
					if (!track.isNull() && track.get("__typename").text().equalsIgnoreCase("track")) {
						tracks.add(this.parseTrackV2(item.get("track"), preview, albumImage));
					}
				}
			}
			return new SpotifyAudioPlaylist(albumName, tracks, ExtendedAudioPlaylist.Type.ALBUM, albumUrl,
				albumImage, albumArtist, tracks.size());
		} catch (JsonProcessingException ex) {
			throw new IOException(ex);
		}
	}

	public AudioItem getAnonymousPlaylist(String id, boolean preview) throws IOException {
		try {
			JsonBrowser json = this.partnerApiClient.getPlaylist("spotify:playlist:" + id, 0, 50);

			if (json == null || json.get("data").get("playlistV2").isNull()) {
				return AudioReference.NO_TRACK;
			}

			JsonBrowser playlistData = json.get("data").get("playlistV2");

			String playlistName = playlistData.get("name").text();
			String playlistUrl = "https://open.spotify.com/playlist/" + id;

			String playlistImage = null;
			if (playlistData.get("images").get("items").isList()
				&& !playlistData.get("images").get("items").values().isEmpty()) {
				JsonBrowser imageItem = playlistData.get("images").get("items").index(0);
				if (imageItem.get("sources").isList() && !imageItem.get("sources").values().isEmpty()) {
					playlistImage = imageItem.get("sources").index(0).get("url").text();

				}
			}

			String playlistOwner = playlistData.get("ownerV2").get("data").get("name").text();

			JsonBrowser tracksJson = playlistData.get("content").get("items");
			List<AudioTrack> tracks = new ArrayList<>();
			for (var item : tracksJson.values()) {
				if (item.get("itemV2").get("data").get("__typename").text().toLowerCase().equals("track")) {
					tracks.add(this.parseTrackV2(item, preview));
				}
			}
			return new SpotifyAudioPlaylist(playlistName, tracks, ExtendedAudioPlaylist.Type.PLAYLIST, playlistUrl,
				playlistImage, playlistOwner, tracks.size());
		} catch (JsonProcessingException e) {
			throw new IOException(e);
		}
	}

	public AudioItem getPlaylist(String id, boolean preview) throws IOException {

		var anonymous = id.startsWith("37i9dQZ");

		if (anonymous) {
			return this.getAnonymousPlaylist(id, preview);
		}

		JsonBrowser json;
		try {
			json = this.getJson(API_BASE + "playlists/" + id, anonymous, this.preferAnonymousToken);
		} catch (RateLimitException | SpotifyWebApiFallbackException e) {
			log.warn("Spotify Web API unavailable, switching to partner API for playlist {}", id);
			return this.getAnonymousPlaylist(id, preview);
		}

		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		JsonBrowser page;
		var offset = 0;
		var pages = 0;
		do {
			try {
				page = this.getJson(
					API_BASE + "playlists/" + id + "/tracks?limit=" + PLAYLIST_MAX_PAGE_ITEMS + "&offset=" + offset,
					anonymous, this.preferAnonymousToken);
			} catch (RateLimitException | SpotifyWebApiFallbackException e) {
				log.warn("Spotify Web API unavailable while paging playlist {}, switching to partner API", id);
				return this.getAnonymousPlaylist(id, preview);
			}
			offset += PLAYLIST_MAX_PAGE_ITEMS;

			for (var value : page.get("items").values()) {
				var track = value.get("track");
				if (track.isNull() || track.get("type").text().equals("episode")
					|| (!this.localFiles && track.get("is_local").asBoolean(false))) {
					continue;
				}

				tracks.add(this.parseTrack(track, preview));
			}

		} while (page.get("next").text() != null && ++pages < this.playlistPageLimit);

		return new SpotifyAudioPlaylist(json.get("name").safeText(), tracks, ExtendedAudioPlaylist.Type.PLAYLIST,
			json.get("external_urls").get("spotify").text(), json.get("images").index(0).get("url").text(),
			json.get("owner").get("display_name").text(), (int) json.get("tracks").get("total").asLong(0));
	}

	public AudioTrack parseTrackV2(JsonBrowser data, boolean isPreview) {
		return parseTrackV2(data, isPreview, null);
	}
	public AudioTrack parseTrackV2(JsonBrowser data, boolean isPreview, String albumArtworkUrl) {
		JsonBrowser trackData = data.get("itemV2").get("data");
		if (trackData.isNull()) {
			trackData = data;
		}

		String title = trackData.get("name").text();
		var length = trackData.get("trackDuration").get("totalMilliseconds").asLong(0);
		if (length == 0) {
			length = trackData.get("duration").get("totalMilliseconds").asLong(0);
		}
		if (length == 0) {
			length = trackData.get("duration_ms").asLong(0);
		}

		String author = trackData.get("artists").get("items").values().stream()
			.map(m -> m.get("profile").get("name").text()).collect(Collectors.joining(","));

		String spotifyUri = trackData.get("uri").text();
		String identifier = spotifyUri != null ? spotifyUri.replace("spotify:track:", "") : trackData.get("id").text();
		String uri = "https://open.spotify.com/track/" + identifier;

		String albumName = trackData.get("albumOfTrack").get("name").text();

		String albumUri = trackData.get("albumOfTrack").get("uri").text();
		String albumUrl = albumUri != null ? "https://open.spotify.com/album/" + albumUri.replace("spotify:album:", "") : null;

		String artworkUrl = albumArtworkUrl;
		if (artworkUrl == null) {
			JsonBrowser sources = trackData.get("albumOfTrack").get("coverArt").get("sources");
			if (sources.isList() && sources.values().size() != 0) {
				int size = sources.values().size();
				artworkUrl = sources.values().get(size - 1).get("url").text();
			}
		}

		String isrc = trackData.get("externalIds").get("isrc").text();
		if (isNullOrBlank(isrc)) {
			isrc = trackData.get("external_ids").get("isrc").text();
		}
		if (isNullOrBlank(isrc) && !isNullOrBlank(identifier)) {
			isrc = fetchIsrcViaSpClientMetadata(identifier);
		}

		return new SpotifyAudioTrack(
			new AudioTrackInfo(title, author, length, identifier, false, uri, artworkUrl, isrc), albumName,
			albumUrl, null, null, null, isPreview, this);

	}

	public AudioItem getArtist(String id, boolean preview) throws IOException {
		try {
			var json = this.getJson(API_BASE + "artists/" + id, false, this.preferAnonymousToken);
			if (json == null) {
				return AudioReference.NO_TRACK;
			}

			var tracksJson = this.getJson(API_BASE + "artists/" + id + "/top-tracks?market=" + this.countryCode, false,
				this.preferAnonymousToken);
			if (tracksJson == null || tracksJson.get("tracks").values().isEmpty()) {
				return AudioReference.NO_TRACK;
			}

			for (var track : tracksJson.get("tracks").values()) {
				track.get("artists").index(0).put("images", json.get("images"));
			}

			return new SpotifyAudioPlaylist(json.get("name").safeText() + "'s Top Tracks",
				this.parseTracks(tracksJson, preview), ExtendedAudioPlaylist.Type.ARTIST,
				json.get("external_urls").get("spotify").text(), json.get("images").index(0).get("url").text(),
				json.get("name").text(), (int) tracksJson.get("tracks").get("total").asLong(0));
		} catch (RateLimitException | SpotifyWebApiFallbackException e) {
			try {
				var artistJson = this.getJson(API_BASE + "artists/" + id, false, this.preferAnonymousToken);
				String artistName = (artistJson != null) ? artistJson.get("name").safeText() : null;
				if (artistName == null || artistName.isEmpty()) {
					artistName = "spotify artist " + id;
				}
				log.warn("Spotify Web API unavailable, switching to partner API search fallback for artist {} ({})", id,
					artistName);
				return this.getSearch(artistName + " top tracks", preview);
			} catch (RateLimitException | SpotifyWebApiFallbackException ignored) {
				log.warn("Spotify Web API unavailable, switching to partner API search fallback for artist {}", id);
				return this.getSearch("spotify artist " + id + " top tracks", preview);
			}
		}
	}

	public AudioItem getTrack(String id, boolean preview) throws IOException {
		try {
			var json = this.getJson(API_BASE + "tracks/" + id, false, this.preferAnonymousToken);
			if (json != null) {
				var artistJson = this.getJson(API_BASE + "artists/" + json.get("artists").index(0).get("id").text(), false,
					this.preferAnonymousToken);
				if (artistJson != null) {
					json.get("artists").index(0).put("images", artistJson.get("images"));
				}
				return this.parseTrack(json, preview);
			}
			log.warn("Main API failed for track {}, trying partner API as fallback", id);
			return this.getPartnerTrack(id, preview);
		} catch (RateLimitException | SpotifyWebApiFallbackException e) {
			log.warn("Spotify Web API unavailable, switching to partner API for track {}", id);
			return this.getPartnerTrack(id, preview);
		} catch (Exception e) {
			if (e instanceof IOException) throw (IOException) e;
			throw new IOException(e);
		}
	}

	private AudioItem getPartnerTrack(String id, boolean preview) throws IOException {
		try {
			JsonBrowser responseJson = this.partnerApiClient.getTrack("spotify:track:" + id);
			if (responseJson == null || responseJson.get("data").get("trackUnion").isNull()) {
				return AudioReference.NO_TRACK;
			}

			JsonBrowser trackData = responseJson.get("data").get("trackUnion");
			return this.parsePartnerTrack(trackData, preview);
		} catch (JsonProcessingException e) {
			throw new IOException(e);
		}
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
		String isrc = json.get("external_ids").get("isrc").text();
		String id = json.get("id").text() != null ? json.get("id").text() : "local";
		if (isNullOrBlank(isrc) && !"local".equals(id)) {
			String fallbackIsrc = fetchIsrcViaSpClientMetadata(id);
			if (!isNullOrBlank(fallbackIsrc)) {
				isrc = fallbackIsrc;
			}
		}
		return new SpotifyAudioTrack(
			new AudioTrackInfo(
				json.get("name").safeText(),
				json.get("artists").index(0).get("name").safeText().isEmpty() ? "Unknown"
					: json.get("artists").index(0).get("name").safeText(),
				preview ? PREVIEW_LENGTH : json.get("duration_ms").asLong(0),
				id,
				false,
				json.get("external_urls").get("spotify").text(),
				json.get("album").get("images").index(0).get("url").text(),
				isrc),
			json.get("album").get("name").text(),
			json.get("album").get("external_urls").get("spotify").text(),
			json.get("artists").index(0).get("external_urls").get("spotify").text(),
			json.get("artists").index(0).get("images").index(0).get("url").text(),
			json.get("preview_url").text(),
			preview,
			this);
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

