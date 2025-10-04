package com.github.topi314.lavasrc.vkmusic;

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
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VkMusicSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable, AudioLyricsManager, AudioSearchManager {
	private static final Pattern VK_PLAYLIST_HEADER_REGEX = Pattern.compile("(https?://)?(?:www\\.)?vk\\.(?:com|ru)/audios\\d+\\?q=[^&]+&z=audio_playlist(?<owner>-?[A-Za-z\\d]+)_(?<id>-?[A-Za-z\\d]+)(?<accessKey>_([^/?#]*))?(?:[/?#].*)?");
	private static final Pattern VK_PLAYLIST_TYPE_REGEX = Pattern.compile("(https?://)?(?:www\\.)?vk\\.(?:com|ru)/music/(playlist|album)/(?<owner>-?[A-Za-z\\d]+)_(?<id>-?[A-Za-z\\d]+)(?<accessKey>_([^/?#]*))?(?:[/?#].*)?");
	private static final Pattern VK_TRACK_REGEX = Pattern.compile("(https?://)?(?:www\\.)?vk\\.(?:com|ru)/audio(?<id>-?\\d+)_(?<artistId>-?\\d+)(?<accessKey>_([^/?#]*))?(?:[/?#].*)?");
	private static final Pattern VK_ARTIST_REGEX = Pattern.compile("(https?://)?(?:www\\.)?vk\\.(?:com|ru)/artist/(?<artistId>[^/?#]+)");

	public static final String SEARCH_PREFIX = "vksearch:";
	public static final String RECOMMENDATIONS_PREFIX = "vkrec:";
	public static final String PUBLIC_API_BASE = "https://api.vk.com/method/";
	public static final String API_VERSION = "5.199";
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK, AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.ARTIST);

	private static final Logger log = LoggerFactory.getLogger(VkMusicSourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager;

	private String userToken;
	private int artistLoadLimit;
	private int playlistLoadLimit;
	private int recommendationsLoadLimit;

	public VkMusicSourceManager(String userToken) {
		if (userToken == null || userToken.isEmpty()) {
			throw new IllegalArgumentException("Vk Music user token must be set");
		}
		this.userToken = userToken;
		this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	}

	public void setUserToken(String userToken) {
		if (userToken == null || userToken.isEmpty()) {
			throw new IllegalArgumentException("Vk Music user token must be set");
		}

		this.userToken = userToken;
	}

	public void setArtistLoadLimit(int artistLimit) {
		this.artistLoadLimit = artistLimit;
	}

	public void setPlaylistLoadLimit(int playlistLimit) {
		this.playlistLoadLimit = playlistLimit;
	}

	public void setRecommendationsLoadLimit(int recommendationsLimit) {
		this.recommendationsLoadLimit = recommendationsLimit;
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "vkmusic";
	}

	@Override
	public @Nullable AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
		if (types.isEmpty()) {
			types = SEARCH_TYPES;
		}
		if (query.startsWith(SEARCH_PREFIX)) {
			try {
				return getSearchResult(query.substring(SEARCH_PREFIX.length()), types);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	private AudioSearchResult getSearchResult(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) throws IOException {
		var albums = new ArrayList<AudioPlaylist>();
		var artists = new ArrayList<AudioPlaylist>();
		var playlists = new ArrayList<AudioPlaylist>();
		var tracks = new ArrayList<AudioTrack>();

		if (types.contains(AudioSearchResult.Type.ALBUM)) {
			var playlistResponse = this.getJson("audio.searchAlbums", "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&count=20");
			if (playlistResponse != null && !playlistResponse.get("response").isNull() && !playlistResponse.get("response").get("items").values().isEmpty()) {
				albums.addAll(this.parsePlaylistSearch(
					playlistResponse.get("response").get("items").values(),
					ExtendedAudioPlaylist.Type.ALBUM
				));
			}
		}

		if (types.contains(AudioSearchResult.Type.ARTIST)) {
			var artistResponse = this.getJson("audio.searchArtists", "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&count=20");
			if (artistResponse != null && !artistResponse.get("response").isNull() && !artistResponse.get("response").get("items").values().isEmpty()) {
				for (var artist : artistResponse.get("response").get("items").values()) {
					String artworkUrl = null;
					if (!artist.get("photo").values().isEmpty()) {
						artworkUrl = artist.get("photo").values().stream()
							.max(Comparator.comparingLong(item -> item.get("width").asLong(0)))
							.map(item -> item.get("url").text())
							.orElse(null);
					}
					artists.add(new VkMusicAudioPlaylist(
						artist.get("name").text() + "'s Top Tracks",
						Collections.emptyList(),
						ExtendedAudioPlaylist.Type.ARTIST,
						"https://vk.com/artist/" + artist.get("domain").text(),
						artworkUrl,
						artist.get("name").text(),
						0
					));
				}
			}
		}

		if (types.contains(AudioSearchResult.Type.PLAYLIST)) {
			var playlistResponse = this.getJson("audio.searchPlaylists", "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&count=20");
			if (playlistResponse != null && !playlistResponse.get("response").isNull() && !playlistResponse.get("response").get("items").values().isEmpty()) {
				playlists.addAll(this.parsePlaylistSearch(
					playlistResponse.get("response").get("items").values(),
					ExtendedAudioPlaylist.Type.PLAYLIST
				));
			}
		}

		if (types.contains(AudioSearchResult.Type.TRACK)) {
			tracks.addAll(((BasicAudioPlaylist) this.getSearch(query)).getTracks());
		}

		return new BasicAudioSearchResult(tracks, albums, artists, playlists, new ArrayList<>());
	}

	private List<VkMusicAudioPlaylist> parsePlaylistSearch(List<JsonBrowser> playlistItems, ExtendedAudioPlaylist.Type type) {
		return playlistItems.stream()
			.map(playlistItem -> new VkMusicAudioPlaylist(
				playlistItem.get("title").text(),
				Collections.emptyList(),
				type,
				"https://vk.com/music/"
					+ (type == ExtendedAudioPlaylist.Type.PLAYLIST ? "playlist" : "album") + "/"
					+ playlistItem.get("owner_id").text()
					+ "_" + playlistItem.get("id").text()
					+ (playlistItem.get("access_key").isNull() ? "" : "_" + playlistItem.get("access_key").text()),
				this.parsePlaylistThumbnail(playlistItem),
				this.parseAlbumAuthor(playlistItem),
				playlistItem.get("count").as(Integer.class)
			))
			.collect(Collectors.toList());
	}

	@Override
	public @Nullable AudioLyrics loadLyrics(@NotNull AudioTrack track) {
		if (track.getSourceManager() instanceof VkMusicSourceManager) {
			try {
				var json = this.getJson("audio.getLyrics", "&audio_id=" + track.getIdentifier());
				if (!json.get("error").isNull() || !json.get("response").isNull() && !json.get("response").get("lyrics").isNull()) {
					return null;
				}

				if (!json.get("response").get("lyrics").get("timestamps").values().isEmpty()) {
					return this.parseTimestampsLyrics(json.get("response").get("lyrics").get("timestamps").values());
				} else if (!json.get("response").get("lyrics").get("text").values().isEmpty()) {
					return this.parseTextLyrics(json.get("response").get("lyrics").get("text").values());
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	private BasicAudioLyrics parseTimestampsLyrics(@NotNull List<JsonBrowser> lines) {
		var strokes = new ArrayList<AudioLyrics.Line>();
		var text = lines.stream().map(line -> {
			strokes.add(
				new BasicAudioLyrics.BasicLine(
					Duration.ofMillis(line.get("begin").as(Long.class)),
					Duration.ofMillis(line.get("end").as(Long.class) - line.get("begin").as(Long.class)),
					line.get("line").text()
				)
			);
			return line.get("line").text();
		}).collect(Collectors.joining(" "));

		return new BasicAudioLyrics(
			this.getSourceName(),
			"LyricFind",
			text,
			strokes
		);
	}

	private BasicAudioLyrics parseTextLyrics(@NotNull List<JsonBrowser> lines) {
		var strokes = new ArrayList<AudioLyrics.Line>();
		var text = lines.stream()
			.filter(line -> !line.text().isEmpty())
			.map(line -> {
				strokes.add(new BasicAudioLyrics.BasicLine(
					Duration.ZERO,
					Duration.ZERO,
					line.text()
				));
				return line.text();
			}).collect(Collectors.joining(" "));

		return new BasicAudioLyrics(
			this.getSourceName(),
			"LyricFind",
			text,
			strokes
		);
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
			var uri = reference.identifier;

			var playlistFromHeader = VK_PLAYLIST_HEADER_REGEX.matcher(uri);
			if (playlistFromHeader.find()) {
				if (playlistFromHeader.group("owner") != null && playlistFromHeader.group("id") != null) {
					return this.getPlaylist(
						playlistFromHeader.group("owner"),
						playlistFromHeader.group("id"),
						playlistFromHeader.group("accessKey")
					);
				}
			}

			var playlistMatcher = VK_PLAYLIST_TYPE_REGEX.matcher(uri);
			if (playlistMatcher.find()) {
				if (playlistMatcher.group("owner") != null && playlistMatcher.group("id") != null) {
					return this.getPlaylist(
						playlistMatcher.group("owner"),
						playlistMatcher.group("id"),
						playlistMatcher.group("accessKey")
					);
				}
			}

			var trackMatcher = VK_TRACK_REGEX.matcher(uri);
			if (trackMatcher.find() && trackMatcher.group("id") != null && trackMatcher.group("artistId") != null) {
				return this.getTrack(trackMatcher.group("id") + "_" + trackMatcher.group("artistId"));
			}

			var artistMatcher = VK_ARTIST_REGEX.matcher(uri);
			if (artistMatcher.find()) {
				return this.getArtist(artistMatcher.group("artistId"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	private AudioItem getRecommendations(String audioId) throws IOException {
		var json = this.getJson("audio.getRecommendations", "&target_audio=" + audioId + "&count=" + this.recommendationsLoadLimit);
		if (json.isNull() || json.get("response").isNull()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("response").get("items"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new VkMusicAudioPlaylist(
			"Vk Music Recommendations",
			tracks,
			ExtendedAudioPlaylist.Type.RECOMMENDATIONS,
			null,
			null,
			null,
			tracks.size()
		);
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson("audio.search", "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&sort=2");

		if (json.isNull() || json.get("response").isNull()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = this.parseTracks(json.get("response").get("items"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Vk Music Search: " + query, tracks, null, true);
	}

	private AudioItem getPlaylist(String owner_id, String playlist_id, String accessKey) throws IOException {
		var query = "&owner_id=" + owner_id;
		if (accessKey != null) {
			query += "&access_key=" + accessKey;
		}
		var json = this.getJson("audio.get", query + "&album_id=" + playlist_id + "&count=" + playlistLoadLimit * 50);

		if (
			json.isNull()
				|| json.get("response").isNull()
				|| json.get("response").get("items").isNull()
				|| json.get("response").get("items").values().isEmpty()
		) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(json.get("response").get("items"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		String coverUri = null;
		String title = null;
		var playlistJson = this.getJson("audio.getPlaylistById", query + "&playlist_id=" + playlist_id)
			.get("response");

		if (!playlistJson.isNull()) {
			coverUri = this.parsePlaylistThumbnail(playlistJson);

			if (!playlistJson.get("title").isNull()) {
				title = playlistJson.get("title").text();
			}
		}

		var type = playlistJson.get("type").as(Long.class) == 1;
		return new VkMusicAudioPlaylist(
			title,
			tracks,
			type ? ExtendedAudioPlaylist.Type.ALBUM : ExtendedAudioPlaylist.Type.PLAYLIST,
			"https://vk.com/music/" + (type ? "album" : "playlist") + "/"
				+ owner_id
				+ "_" + playlist_id
				+ (playlistJson.get("access_key").isNull() ? "" : "_" + playlistJson.get("access_key").text()),
			coverUri,
			this.parseAlbumAuthor(playlistJson),
			tracks.size()
		);
	}

	private String parseAlbumAuthor(JsonBrowser json) {
		if (!json.get("main_artists").values().isEmpty()) {
			return json.get("main_artists").values().stream()
				.map(node -> node.get("name").text())
				.collect(Collectors.joining(", "));
		}
		return null;
	}

	private String parsePlaylistThumbnail(JsonBrowser json) {
		if (!json.get("photo").isNull()) {
			return json.get("photo").get("photo_600").text();
		}
		if (!json.get("thumbs").isNull()) {
			return json.get("thumbs").values().get(0).get("photo_600").text();
		}
		return null;
	}

	private AudioItem getTrack(String id) throws IOException {
		var json = this.getJson("audio.getById", "&audios=" + id);
		if (json.isNull() || json.get("response").isNull()) {
			return AudioReference.NO_TRACK;
		}

		var track = this.parseTrack(json.get("response").values().get(0));
		if (track == null) {
			return AudioReference.NO_TRACK;
		}
		return track;
	}

	private AudioItem getArtist(String id) throws IOException {
		var json = this.getJson("audio.getAudiosByArtist", "&artist_id=" + id + "&count=" + artistLoadLimit * 20);
		if (json.isNull() || json.get("response").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(json.get("response").get("items"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artistJson = this.getJson("audio.getArtistById", "&artist_id=" + id)
			.get("response");

		if (artistJson.isNull()) {
			return AudioReference.NO_TRACK;
		}

		var author = artistJson.get("name").text();
		return new VkMusicAudioPlaylist(
			author + "'s Top Tracks",
			tracks,
			ExtendedAudioPlaylist.Type.ARTIST,
			"https://vk.com/artist/" + artistJson.get("domain").text(),
			null,
			author,
			tracks.size()
		);
	}

	public JsonBrowser getJson(String method, String headers) throws IOException {
		var uri = PUBLIC_API_BASE + method + "?v=" + API_VERSION + headers + "&access_token=" + this.userToken;
		var request = new HttpGet(uri);
		request.setHeader("Content-Type", "application/json");
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.values()) {
			AudioTrack trackInfo = this.parseTrack(track);
			if (trackInfo != null) {
				tracks.add(trackInfo);
			}
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json) {
		try {
			if (json.get("url").isNull() || json.get("url").text().isEmpty()) {
				return null;
			}
			String coverUri = null;
			if (!json.get("album").isNull() && !json.get("album").get("thumb").isNull()) {
				coverUri = json.get("album").get("thumb").get("photo_600").text();
			}

			String albumTitle = null;
			String albumUrl = null;
			if (!json.get("album").isNull()) {
				albumTitle = json.get("album").get("title").text();
				albumUrl = "https://vk.com/music/album/" + json.get("album").get("owner_id").text() + "_" + json.get("album").get("id").text();
			}

			String artistUrl = null;
			if (
				!json.get("main_artists").isNull()
					&& !json.get("main_artists").values().isEmpty()
					&& !json.get("main_artists").values().get(0).get("domain").isNull()
			) {
				artistUrl = "https://vk.com/artist/" + json.get("main_artists").values().get(0).get("domain").text();
			}

			var audioId = json.get("owner_id").text() + "_" + json.get("id").text();
			return new VkMusicAudioTrack(
				new AudioTrackInfo(
					json.get("title").text(),
					json.get("artist").text(),
					json.get("duration").as(Long.class) * 1000,
					audioId,
					false,
					"https://vk.com/audio" + audioId,
					coverUri,
					null
				),
				albumTitle,
				albumUrl,
				artistUrl,
				null,
				json.get("url").text(),
				this
			);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new VkMusicAudioTrack(
			trackInfo,
			extendedAudioTrackInfo.albumName,
			extendedAudioTrackInfo.albumUrl,
			extendedAudioTrackInfo.artistUrl,
			extendedAudioTrackInfo.artistArtworkUrl,
			extendedAudioTrackInfo.previewUrl,
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