package com.github.topi314.lavasrc.yandexmusic;

import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class YandexMusicSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable, AudioLyricsManager, AudioSearchManager {
	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?music\\.yandex\\.(?<domain>ru|com|kz|by)/(?<type1>artist|album|track)/(?<identifier>[0-9]+)(/(?<type2>track)/(?<identifier2>[0-9]+))?/?");
	public static final Pattern URL_PLAYLIST_PATTERN = Pattern.compile("(https?://)?music\\.yandex\\.(?<domain>ru|com|kz|by)/users/(?<identifier>[0-9A-Za-z@.-]+)/playlists/(?<identifier2>[0-9]+)/?");
	public static final Pattern URL_PLAYLIST_UUID_PATTERN = Pattern.compile("(https?://)?music\\.yandex\\.(?<domain>ru|com|kz|by)/playlists/(?<identifier>[0-9A-Za-z\\-.]+)");
	public static final Pattern EXTRACT_LYRICS_STROKE = Pattern.compile("\\[(?<min>\\d{2}):(?<sec>\\d{2})\\.(?<mil>\\d{2})] ?(?<text>.+)?");
	public static final String SEARCH_PREFIX = "ymsearch:";
	public static final String RECOMMENDATIONS_PREFIX = "ymrec:";
	public static final String PUBLIC_API_BASE = "https://api.music.yandex.net";
	public static final int ARTIST_MAX_PAGE_ITEMS = 10;
	public static final int PLAYLIST_MAX_PAGE_ITEMS = 100;
	public static final int ALBUM_MAX_PAGE_ITEMS = 50;
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK, AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.ARTIST);

	private static final Logger log = LoggerFactory.getLogger(YandexMusicSourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager;

	private String accessToken;
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

	public void setAccessToken(String accessToken) {
		if (accessToken == null || accessToken.isEmpty()) {
			throw new IllegalArgumentException("Yandex Music accessToken must be set");
		}
		this.accessToken = accessToken;
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

	@NotNull
	@Override
	public String getSourceName() {
		return "yandexmusic";
	}

	private AudioSearchResult getSearchResult(String query, Set<AudioSearchResult.Type> setOfTypes) throws IOException {
		var json = this.getJson(
			PUBLIC_API_BASE + "/search"
				+ "?text=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
				+ "&type=all"
				+ "&page=0"
		);
		if (json == null) {
			return AudioSearchResult.EMPTY;
		}
		if (setOfTypes.isEmpty()) {
			setOfTypes = SEARCH_TYPES;
		}

		var resultJson = json.get("result");
		if (resultJson.isNull()) {
			return AudioSearchResult.EMPTY;
		}

		var albums = new ArrayList<AudioPlaylist>();
		var artists = new ArrayList<AudioPlaylist>();
		var playlists = new ArrayList<AudioPlaylist>();
		var tracks = new ArrayList<AudioTrack>();

		if (setOfTypes.contains(AudioSearchResult.Type.ALBUM)) {
			for (var albumsJson : resultJson.get("albums").get("results").values()) {
				if (!albumsJson.get("available").asBoolean(false)) {
					continue;
				}

				albums.add(new YandexMusicAudioPlaylist(
					albumsJson.get("title").text(),
					tracks,
					ExtendedAudioPlaylist.Type.ALBUM,
					"https://music.yandex.com/album/" + albumsJson.get("id").text(),
					this.parseCoverUri(albumsJson),
					this.parseArtist(albumsJson),
					(int) albumsJson.get("trackCount").asLong(0)
				));
			}
		}

		if (setOfTypes.contains(AudioSearchResult.Type.ARTIST)) {
			for (var artistJson : resultJson.get("artists").get("results").values()) {
				if (!artistJson.get("available").asBoolean(false)) {
					continue;
				}

				var authorName = artistJson.get("name").text();
				artists.add(new YandexMusicAudioPlaylist(
					authorName + "'s Top Tracks",
					Collections.emptyList(),
					YandexMusicAudioPlaylist.Type.ARTIST,
					"https://music.yandex.com/artist/" + artistJson.get("id").text(),
					this.parseCoverUri(artistJson),
					authorName,
					artistJson.get("counts").isNull() ? null : ((int) artistJson.get("counts").get("tracks").asLong(0))
				));
			}
		}

		if (setOfTypes.contains(AudioSearchResult.Type.PLAYLIST) && !resultJson.get("playlists").get("results").isNull()) {
			for (var playlistJson : resultJson.get("playlists").get("results").values()) {
				if (!playlistJson.get("available").asBoolean(false)) {
					continue;
				}

				var name = "";
				if (!playlistJson.get("owner").isNull()) {
					if (!playlistJson.get("owner").get("name").isNull()) {
						name = playlistJson.get("owner").get("name").text();
					} else {
						name = playlistJson.get("owner").get("login").text();
					}
				}

				playlists.add(new YandexMusicAudioPlaylist(
					name,
					Collections.emptyList(),
					YandexMusicAudioPlaylist.Type.PLAYLIST,
					"https://music.yandex.com/users/" + playlistJson.get("owner").get("login").text() + "/playlists/" + playlistJson.get("kind").text(),
					this.parseCoverUri(playlistJson),
					playlistJson.get("owner").get("name").text(),
					(int) playlistJson.get("trackCount").asLong(0)
				));
			}
		}

		if (setOfTypes.contains(AudioSearchResult.Type.TRACK) && !resultJson.get("tracks").get("results").isNull()) {
			tracks.addAll(this.parseTracks(resultJson.get("tracks").get("results"), "com"));
		}

		return new BasicAudioSearchResult(tracks, albums, artists, playlists, new ArrayList<>());
	}

	@Override
	public @Nullable AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> setOfTypes) {
		try {
			if (query.startsWith(SEARCH_PREFIX)) {
				return this.getSearchResult(query.substring(SEARCH_PREFIX.length()), setOfTypes);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	private JsonBrowser findLyrics(String identifier) throws IOException {
		var sign = YandexMusicSign.create(identifier);
		return this.getJson(
			PUBLIC_API_BASE + "/tracks/" + identifier + "/lyrics"
				+ "?format=LRC"
				+ "&timeStamp=" + sign.timestamp
				+ "&sign=" + sign.value
		);
	}

	@Override
	public @Nullable AudioLyrics loadLyrics(@NotNull AudioTrack track) throws IllegalStateException {
		if (track.getSourceManager() instanceof YandexMusicSourceManager) {
			try {
				var lyricsJson = findLyrics(track.getIdentifier());
				if (lyricsJson != null && !lyricsJson.isNull() && !lyricsJson.get("result").isNull()) {
					return this.parseLyrics(
						lyricsJson.get("result").get("downloadUrl").text(),
						track,
						lyricsJson.get("result").get("major").isNull() ? null : lyricsJson.get("result").get("major").get("name").text()
					);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	@NotNull
	private BasicAudioLyrics parseLyrics(String downloadUrl, AudioTrack track, String provider) throws IOException {
		var lyrics = new ArrayList<AudioLyrics.Line>();
		var lines = this.getDownloadStrings(downloadUrl, "downloadinfo-text-page");
		var allText = new StringBuilder();

		for (int i = 0; i < lines.length; i++) {
			var lyricsLine = this.extractLine(lines[i]);
			if (lyricsLine == null || lyricsLine.getLine().isEmpty()) {
				continue;
			}

			Duration nextTimestamp;
			if (i + 1 < lines.length) {
				var line = this.extractLine(lines[i + 1]);
				nextTimestamp = line != null ? line.getTimestamp() : Duration.ofMillis(track.getDuration());
			} else {
				nextTimestamp = Duration.ofMillis(track.getDuration());
			}

			allText.append(lyricsLine.getLine()).append("\n");
			lyrics.add(new BasicAudioLyrics.BasicLine(
				lyricsLine.getTimestamp(),
				Duration.ofMillis(Math.max(nextTimestamp.toMillis() - lyricsLine.getTimestamp().toMillis(), 0)),
				lyricsLine.getLine()
			));
		}

		return new BasicAudioLyrics(
			"yandexmusic",
			provider,
			allText.toString(),
			lyrics
		);
	}

	@Nullable
	private BasicAudioLyrics.BasicLine extractLine(String line) {
		var matcher = EXTRACT_LYRICS_STROKE.matcher(line);
		if (matcher.find()) {
			var timestampMillis = (
				(Integer.parseInt(matcher.group("min")) * 60 * 1000)
					+ (Integer.parseInt(matcher.group("sec")) * 1000)
					+ (Integer.parseInt(matcher.group("mil")) * 10)
			);
			return new BasicAudioLyrics.BasicLine(
				Duration.ofMillis(timestampMillis),
				Duration.ofMillis(0),
				(matcher.group("text") == null) ? "" : matcher.group("text")
			);
		}
		return null;
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
				var domainEnd = matcher.group("domain");
				switch (matcher.group("type1")) {
					case "album":
						if (matcher.group("type2") != null) {
							var trackId = matcher.group("identifier2");
							return this.getTrack(trackId, domainEnd);
						}
						var albumId = matcher.group("identifier");
						return this.getAlbum(albumId, domainEnd);
					case "artist":
						var artistId = matcher.group("identifier");
						return this.getArtist(artistId, domainEnd);
					case "track":
						var trackId = matcher.group("identifier");
						return this.getTrack(trackId, domainEnd);
				}
				return null;
			}
			matcher = URL_PLAYLIST_PATTERN.matcher(reference.identifier);
			if (matcher.find()) {
				var userId = matcher.group("identifier");
				var playlistId = matcher.group("identifier2");
				return this.getPlaylist(userId, playlistId, matcher.group("domain"));
			}
			matcher = URL_PLAYLIST_UUID_PATTERN.matcher(reference.identifier);
			if (matcher.find()) {
				var uuid = matcher.group("identifier");
				return this.getPlaylist(uuid, matcher.group("domain"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	private static boolean canBeLong(String str) {
		try {
			Long.parseLong(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private AudioItem getRecommendations(String identifier) throws IOException {
		if (!canBeLong(identifier)) {
			throw new IllegalArgumentException("The yandex music track identifier must be a number");
		}

		var json = this.getJson(PUBLIC_API_BASE + "/tracks/" + identifier + "/similar");
		if (json == null || json.get("result").isNull() || json.get("result").get("similarTracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("result").get("similarTracks"), "com");
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new YandexMusicAudioPlaylist(
			"Yandex Music Recommendations",
			tracks,
			ExtendedAudioPlaylist.Type.RECOMMENDATIONS,
			null,
			null,
			null,
			tracks.size()
		);
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/search?text=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=track&page=0");
		if (json == null || json.get("result").get("tracks").isNull()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("result").get("tracks").get("results"), "com");
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new BasicAudioPlaylist("Yandex Music Search: " + query, tracks, null, true);
	}

	private AudioItem getAlbum(String id, String domainEnd) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/albums/" + id + "/with-tracks?page-size=" + ALBUM_MAX_PAGE_ITEMS * albumLoadLimit);
		if (json == null || json.get("result").isNull()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = new ArrayList<AudioTrack>();
		for (var volume : json.get("result").get("volumes").values()) {
			for (var track : volume.values()) {
				var parsedTrack = this.parseTrack(track, domainEnd);
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
			"https://music.yandex." + domainEnd + "/album/" + id,
			this.parseCoverUri(json.get("result")),
			author,
			tracks.size()
		);
	}

	private AudioItem getTrack(String id, String domainEnd) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/tracks/" + id);
		if (json == null || json.get("result").values().get(0).get("available").text().equals("false")) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json.get("result").values().get(0), domainEnd);
	}

	private AudioItem getArtist(String id, String domainEnd) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/artists/" + id + "/tracks?page-size=" + ARTIST_MAX_PAGE_ITEMS * artistLoadLimit);
		if (json == null || json.get("result").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(json.get("result").get("tracks"), domainEnd);
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artistJsonResponse = this.getJson(PUBLIC_API_BASE + "/artists/" + id);
		if (artistJsonResponse == null) {
			return AudioReference.NO_TRACK;
		}
		var artistJson = artistJsonResponse.get("result").get("artist");
		var author = artistJson.get("name").text();

		return new YandexMusicAudioPlaylist(
			author + "'s Top Tracks",
			tracks,
			ExtendedAudioPlaylist.Type.ARTIST,
			"https://music.yandex." + domainEnd + "/artist/" + id,
			parseCoverUri(artistJson),
			author,
			tracks.size()
		);
	}

	private AudioItem getPlaylist(String uuid, String domainEnd) throws IOException {
		var json = this.getJson(
			PUBLIC_API_BASE + "/playlist/" + uuid
				+ "?page-size=" + PLAYLIST_MAX_PAGE_ITEMS * playlistLoadLimit
				+ "&rich-tracks=true"
		);

		return this.getPlaylist(json, domainEnd, "https://music.yandex." + domainEnd + "/playlists/" + uuid);
	}

	private AudioItem getPlaylist(String userString, String id, String domainEnd) throws IOException {
		var json = this.getJson(
			PUBLIC_API_BASE + "/users/" + userString + "/playlists/" + id
				+ "?page-size=" + PLAYLIST_MAX_PAGE_ITEMS * playlistLoadLimit
				+ "&rich-tracks=true"
		);

		return this.getPlaylist(json, domainEnd, "https://music.yandex." + domainEnd + "/users/" + userString + "/playlists/" + id);
	}

	private AudioItem getPlaylist(JsonBrowser json, String domainEnd, String playlistUrl) {
		if (json == null || json.get("result").isNull() || json.get("result").get("tracks").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("result").get("tracks"), domainEnd);
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
			playlistUrl,
			this.parseCoverUri(json.get("result")),
			author,
			tracks.size()
		);
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		request.setHeader("Authorization", "OAuth " + this.accessToken);
		request.setHeader("User-Agent", "Yandex-Music-API");
		request.setHeader("X-Yandex-Music-Client", "YandexMusicAndroid/24023621");
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	public String[] getDownloadStrings(String uri, String name) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		request.setHeader("Authorization", "OAuth " + this.accessToken);
		return HttpClientTools.fetchResponseLines(this.httpInterfaceManager.getInterface(), request, name);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, String domainEnd) {
		var tracksToParse = json.values();
		var tracks = new ArrayList<AudioTrack>();
		for (var track : tracksToParse) {
			var parsedTrack = track.get("track").isNull() ? this.parseTrack(track, domainEnd) : this.parseTrack(track.get("track"), domainEnd);
			if (parsedTrack != null) {
				tracks.add(parsedTrack);
			}
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json, String domainEnd) {
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
			albumUrl = "https://music.yandex." + domainEnd + "/album/" + album.get("id").text();
		}

		String artistUrl = null;
		String artistArtworkUrl = null;
		if (!json.get("artists").values().isEmpty()) {
			var firstArtist = json.get("artists").values().get(0);
			artistUrl = "https://music.yandex." + domainEnd + "/artist/" + firstArtist.get("id").text();
			artistArtworkUrl = parseCoverUri(firstArtist);
		}
		return new YandexMusicAudioTrack(
			new AudioTrackInfo(
				json.get("title").text(),
				artist,
				json.get("durationMs").as(Long.class),
				id,
				false,
				"https://music.yandex." + domainEnd + "/track/" + id,
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
