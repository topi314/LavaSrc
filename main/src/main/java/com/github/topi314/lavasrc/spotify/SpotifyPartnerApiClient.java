package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SpotifyPartnerApiClient {

	public static final String PARTNER_API_BASE = "https://api-partner.spotify.com/pathfinder/v1/query";
	public static final String CLIENT_API_BASE = "https://spclient.wg.spotify.com/";
	private static final String TRACK_METADATA_ENDPOINT_PREFIX = CLIENT_API_BASE + "metadata/4/track/";
	private static final long PREVIEW_LENGTH = 30000;
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.178 Spotify/1.2.65.255 Safari/537.36";
	private static final Logger log = LoggerFactory.getLogger(SpotifyPartnerApiClient.class);

	private final SpotifyTokenTracker tokenTracker;
	private final HttpInterface httpInterface;

	public SpotifyPartnerApiClient(SpotifyTokenTracker tokenTracker, HttpInterface httpInterface) {
		this.tokenTracker = tokenTracker;
		this.httpInterface = httpInterface;
	}

	private HttpPost createBaseRequest(SpotifyRequestPayload payload) throws IOException {
		var request = new HttpPost(PARTNER_API_BASE);

		request.setHeader("User-Agent", USER_AGENT);
		request.setHeader("Content-Type", "application/json");
		request.setHeader("Authorization", "Bearer " + this.tokenTracker.getAnonymousAccessToken());
		request.setHeader("Spotify-App-Version", "1.2.80.289.gd6b01cc3");
		request.setHeader("Referer", "https://open.spotify.com/");

		request.setEntity(new StringEntity(payload.serialize(), StandardCharsets.UTF_8));

		return request;
	}

	public JsonBrowser search(String query, int offset, int limit, boolean includeAudiobooks,
	                          boolean includeArtistHasConcertsField, boolean includePreReleases,
	                          boolean includeAuthors, int numberOfTopResults) throws IOException {
		var request = createBaseRequest(SpotifyRequestPayload.forSearch(
			query,
			offset,
			limit,
			includeAudiobooks,
			includeArtistHasConcertsField,
			includePreReleases,
			includeAuthors,
			numberOfTopResults));
		return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
	}

	public List<JsonBrowser> searchTrackItems(String query, int limit) throws IOException {
		var json = this.search(query, 0, limit, true, false, true, false, 5);
		var items = new ArrayList<JsonBrowser>();
		if (json == null) {
			return items;
		}

		for (var item : json.get("data").get("searchV2").get("tracksV2").get("items").values()) {
			var trackData = item.get("item").get("data");
			if (!trackData.isNull()) {
				items.add(trackData);
			}
		}

		return items;
	}

	public AudioItem loadPartnerSearch(String query, boolean preview, SpotifySourceManager sourceManager) throws IOException {
		var items = this.searchTrackItems(query, 10);
		if (items.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		for (var trackData : items) {
			tracks.add(this.parsePartnerTrack(trackData, preview, null, sourceManager));
		}

		return tracks.isEmpty()
			? AudioReference.NO_TRACK
			: new BasicAudioPlaylist("Spotify Search: " + query, tracks, null, true);
	}

	public JsonBrowser getRecommendations(String uri) throws IOException {
		var request = createBaseRequest(SpotifyRequestPayload.forRecommendations(uri));
		return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
	}

	public List<JsonBrowser> getRecommendationTrackItems(String seedTrackId) throws IOException {
		var json = this.getRecommendations("spotify:track:" + seedTrackId);
		var items = new ArrayList<JsonBrowser>();
		if (json == null) {
			return items;
		}

		var recommendationItems = json.get("data").get("internalLinkRecommenderTrack").get("items");
		if (recommendationItems.isNull()) {
			recommendationItems = json.get("data").get("seoRecommendedTrack").get("items");
		}

		if (!recommendationItems.isList()) {
			return items;
		}

		for (var item : recommendationItems.values()) {
			var trackData = item.get("content").get("data");
			if (trackData.isNull()) {
				trackData = item.get("data");
			}

			if (!trackData.isNull() && "Track".equals(trackData.get("__typename").text())) {
				items.add(trackData);
			}
		}

		return items;
	}

	public AudioItem loadPartnerRecommendations(String seedTrackId, boolean preview, SpotifySourceManager sourceManager) throws IOException {
		var items = this.getRecommendationTrackItems(seedTrackId);
		if (items.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		for (var trackData : items) {
			tracks.add(this.parsePartnerRecommendationTrack(trackData, preview, sourceManager));
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
			tracks.size()
		);
	}

	public JsonBrowser getTrack(String uri) throws IOException {
		var request = createBaseRequest(SpotifyRequestPayload.forTrack(uri));
		return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
	}

	@Nullable
	public JsonBrowser getTrackUnion(String trackId) throws IOException {
		var json = this.getTrack("spotify:track:" + trackId);
		if (json == null) {
			return null;
		}

		var trackUnion = json.get("data").get("trackUnion");
		return trackUnion.isNull() ? null : trackUnion;
	}

	public AudioItem loadPartnerTrack(String id, boolean preview, SpotifySourceManager sourceManager) throws IOException {
		var trackData = this.getTrackUnion(id);
		if (trackData == null) {
			return AudioReference.NO_TRACK;
		}

		return this.parsePartnerTrack(trackData, preview, null, sourceManager);
	}

	public JsonBrowser getPlaylist(String uri, int offset, int limit) throws IOException {
		var request = createBaseRequest(SpotifyRequestPayload.forPlaylist(uri, offset, limit));
		return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
	}

	@Nullable
	public JsonBrowser getPlaylistV2(String playlistId, int offset, int limit) throws IOException {
		var json = this.getPlaylist("spotify:playlist:" + playlistId, offset, limit);
		if (json == null) {
			return null;
		}

		var playlistV2 = json.get("data").get("playlistV2");
		return playlistV2.isNull() ? null : playlistV2;
	}

	public List<JsonBrowser> getPlaylistTrackItems(@Nullable JsonBrowser playlistV2) {
		var items = new ArrayList<JsonBrowser>();
		if (playlistV2 == null) {
			return items;
		}

		for (var item : playlistV2.get("content").get("items").values()) {
			if ("track".equalsIgnoreCase(item.get("itemV2").get("data").get("__typename").text())) {
				items.add(item);
			}
		}

		return items;
	}

	public AudioItem loadPartnerPlaylist(String id, boolean preview, int limit, SpotifySourceManager sourceManager) throws IOException {
		var playlistData = this.getPlaylistV2(id, 0, limit);
		if (playlistData == null) {
			return AudioReference.NO_TRACK;
		}

		var playlistName = playlistData.get("name").text();
		var playlistUrl = "https://open.spotify.com/playlist/" + id;

		String playlistImage = null;
		if (playlistData.get("images").get("items").isList() && !playlistData.get("images").get("items").values().isEmpty()) {
			var imageItem = playlistData.get("images").get("items").index(0);
			if (imageItem.get("sources").isList() && !imageItem.get("sources").values().isEmpty()) {
				playlistImage = imageItem.get("sources").index(0).get("url").text();
			}
		}

		var playlistOwner = playlistData.get("ownerV2").get("data").get("name").text();
		var tracksJson = this.getPlaylistTrackItems(playlistData);
		var tracks = new ArrayList<AudioTrack>();
		for (var item : tracksJson) {
			tracks.add(this.parseTrackV2(item, preview, null, sourceManager));
		}

		return new SpotifyAudioPlaylist(playlistName, tracks, ExtendedAudioPlaylist.Type.PLAYLIST, playlistUrl, playlistImage, playlistOwner, tracks.size());
	}

	public JsonBrowser getAlbum(String id, int offset, int limit) throws IOException {
		HttpPost request = createBaseRequest(SpotifyRequestPayload.forAlbum(id, offset, limit));
		return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
	}

	@Nullable
	public JsonBrowser getAlbumUnion(String albumId, int offset, int limit) throws IOException {
		var json = this.getAlbum(albumId, offset, limit);
		if (json == null) {
			return null;
		}

		var albumUnion = json.get("data").get("albumUnion");
		return albumUnion.isNull() ? null : albumUnion;
	}

	public List<JsonBrowser> getAlbumTrackItems(@Nullable JsonBrowser albumUnion) {
		var tracks = new ArrayList<JsonBrowser>();
		if (albumUnion == null) {
			return tracks;
		}

		var tracksV2Items = albumUnion.get("tracksV2").get("items");
		if (tracksV2Items.isList() && !tracksV2Items.values().isEmpty()) {
			for (var item : tracksV2Items.values()) {
				var track = item.get("track");
				if (!track.isNull() && track.get("uri").text() != null && track.get("playability").get("playable").asBoolean(true)) {
					tracks.add(track);
				}
			}
			return tracks;
		}

		var tracksItems = albumUnion.get("tracks").get("items");
		if (tracksItems.isList() && !tracksItems.values().isEmpty()) {
			for (var item : tracksItems.values()) {
				var track = item.get("track");
				if (!track.isNull() && "track".equalsIgnoreCase(track.get("__typename").text())) {
					tracks.add(item);
				}
			}
		}

		return tracks;
	}

	public AudioItem loadPartnerAlbum(String id, boolean preview, int limit, SpotifySourceManager sourceManager) throws IOException {
		var albumData = this.getAlbumUnion(id, 0, limit);
		if (albumData == null) {
			return AudioReference.NO_TRACK;
		}

		var albumName = albumData.get("name").text();
		var albumUrl = "https://open.spotify.com/album/" + id;
		String albumImage = null;
		if (albumData.get("images").get("items").isList() && !albumData.get("images").get("items").values().isEmpty()) {
			var imageItem = albumData.get("images").get("items").index(0);
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

		var tracks = new ArrayList<AudioTrack>();
		for (var partnerTrack : this.getAlbumTrackItems(albumData)) {
			if (partnerTrack.get("track").isNull()) {
				tracks.add(this.parsePartnerTrack(partnerTrack, preview, albumImage, sourceManager));
			} else {
				tracks.add(this.parseTrackV2(partnerTrack.get("track"), preview, albumImage, sourceManager));
			}
		}

		return new SpotifyAudioPlaylist(albumName, tracks, ExtendedAudioPlaylist.Type.ALBUM, albumUrl, albumImage, albumArtist, tracks.size());
	}

	public JsonBrowser getArtist(String id) throws IOException {
		var request = createBaseRequest(SpotifyRequestPayload.forArtist(id));
		return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
	}

	@Nullable
	public JsonBrowser getArtistUnion(String artistId) throws IOException {
		var json = this.getArtist(artistId);
		if (json == null) {
			return null;
		}

		var artistUnion = json.get("data").get("artistUnion");
		return artistUnion.isNull() ? null : artistUnion;
	}

	public List<JsonBrowser> getArtistTopTrackItems(@Nullable JsonBrowser artistUnion) {
		var tracks = new ArrayList<JsonBrowser>();
		if (artistUnion == null) {
			return tracks;
		}

		for (var item : artistUnion.get("discography").get("topTracks").get("items").values()) {
			var trackData = item.get("track");
			if (trackData.isNull()) {
				trackData = item.get("item").get("data");
			}
			if (trackData.isNull()) {
				trackData = item.get("itemV2").get("data");
			}
			if (trackData.isNull()) {
				trackData = item.get("data");
			}

			if (!trackData.isNull() && trackData.get("uri").text() != null) {
				tracks.add(trackData);
			}
		}

		return tracks;
	}

	public AudioItem loadPartnerArtist(String id, boolean preview, SpotifySourceManager sourceManager) throws IOException {
		var artistData = this.getArtistUnion(id);
		if (artistData == null) {
			return AudioReference.NO_TRACK;
		}

		var artistName = artistData.get("profile").get("name").safeText();
		if (artistName.isEmpty()) {
			artistName = "Unknown Artist";
		}

		String artistArtworkUrl = null;
		var avatarSources = artistData.get("visuals").get("avatarImage").get("sources");
		if (!avatarSources.isList() || avatarSources.values().isEmpty()) {
			avatarSources = artistData.get("visuals").get("avatar").get("sources");
		}
		if (avatarSources.isList() && !avatarSources.values().isEmpty()) {
			var best = avatarSources.values().get(0);
			for (var src : avatarSources.values()) {
				if (src.get("height").asLong(0) > best.get("height").asLong(0)) {
					best = src;
				}
			}
			artistArtworkUrl = best.get("url").text();
		}

		var tracks = new ArrayList<AudioTrack>();
		for (var trackData : this.getArtistTopTrackItems(artistData)) {
			tracks.add(this.parsePartnerTrack(trackData, preview, null, sourceManager));
		}

		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new SpotifyAudioPlaylist(
			artistName + "'s Top Tracks",
			tracks,
			ExtendedAudioPlaylist.Type.ARTIST,
			"https://open.spotify.com/artist/" + id,
			artistArtworkUrl,
			artistName,
			tracks.size()
		);
	}

	private AudioTrack parsePartnerTrack(JsonBrowser track, boolean preview, @Nullable String albumArtworkUrl, SpotifySourceManager sourceManager) {
		var title = track.get("name").safeText();
		if (title.isEmpty()) {
			title = "Unknown Title";
		}

		long length = track.get("duration").get("totalMilliseconds").asLong(0);
		if (length == 0) {
			length = track.get("trackDuration").get("totalMilliseconds").asLong(0);
		}
		if (length == 0) {
			length = track.get("duration_ms").asLong(0);
		}

		var spotifyUri = track.get("uri").text();
		var identifier = "";
		if (spotifyUri != null && !spotifyUri.isEmpty()) {
			identifier = spotifyUri.replace("spotify:track:", "");
		} else {
			identifier = track.get("id").text();
		}

		var uri = "https://open.spotify.com/track/" + identifier;

		var artistsJson = track.get("artists").get("items");
		if (artistsJson == null || !artistsJson.isList() || artistsJson.values().isEmpty()) {
			artistsJson = track.get("firstArtist").get("items");
		}

		var author = "Unknown Artist";
		if (artistsJson != null && artistsJson.isList() && !artistsJson.values().isEmpty()) {
			author = parseArtist(artistsJson);
		}

		var album = track.get("albumOfTrack");
		String albumName = "";
		String albumUrl = "";
		var artworkUrl = albumArtworkUrl;

		if (album != null && !album.isNull()) {
			albumName = album.get("name").safeText();
			if (albumName == null) {
				albumName = "";
			}

			var albumUri = album.get("uri").text();
			if (albumUri != null && !albumUri.isEmpty()) {
				var albumId = albumUri.replace("spotify:album:", "");
				albumUrl = "https://open.spotify.com/album/" + albumId;
			}

			if (artworkUrl == null) {
				var sources = album.get("coverArt").get("sources");
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
			isrc = this.fetchIsrcViaSpClientMetadata(identifier);
		}

		String previewUrl = null;
		if (track.get("previews") != null && track.get("previews").get("audioPreviews") != null && track.get("previews").get("audioPreviews").get("items") != null && !track.get("previews").get("audioPreviews").get("items").values().isEmpty()) {
			previewUrl = track.get("previews").get("audioPreviews").get("items").values().get(0).get("url").text();
		}

		String artistUrl = null;
		String artistArtworkUrl = null;
		if (track.get("artists") != null && track.get("artists").get("items") != null && !track.get("artists").get("items").values().isEmpty()) {
			var artist = track.get("artists").get("items").values().get(0);
			if (artist.get("uri") != null) {
				artistUrl = "https://open.spotify.com/artist/" + artist.get("uri").text().replace("spotify:artist:", "");
			}
			if (artist.get("visuals") != null && artist.get("visuals").get("avatarImage") != null && artist.get("visuals").get("avatarImage").get("sources") != null && !artist.get("visuals").get("avatarImage").get("sources").values().isEmpty()) {
				var best = artist.get("visuals").get("avatarImage").get("sources").values().get(0);
				for (var src : artist.get("visuals").get("avatarImage").get("sources").values()) {
					if (src.get("height").asLong(0) > best.get("height").asLong(0)) {
						best = src;
					}
				}
				artistArtworkUrl = best.get("url").text();
			}
		}

		return new SpotifyAudioTrack(
			new AudioTrackInfo(title, author, preview ? PREVIEW_LENGTH : length, identifier, false, uri, artworkUrl, isrc),
			albumName,
			albumUrl,
			artistUrl,
			artistArtworkUrl,
			previewUrl,
			preview,
			sourceManager
		);
	}

	private AudioTrack parsePartnerRecommendationTrack(JsonBrowser track, boolean preview, SpotifySourceManager sourceManager) {
		var title = track.get("name").safeText();
		if (title.isEmpty()) {
			title = "Unknown Title";
		}

		long length = track.get("duration").get("totalMilliseconds").asLong(0);
		if (length == 0) {
			length = track.get("trackDuration").get("totalMilliseconds").asLong(0);
		}
		if (length == 0) {
			length = track.get("duration_ms").asLong(0);
		}

		var spotifyUri = track.get("uri").text();
		var identifier = spotifyUri != null && !spotifyUri.isEmpty() ? spotifyUri.replace("spotify:track:", "") : track.get("id").text();
		var uri = "https://open.spotify.com/track/" + identifier;

		var artistsJson = track.get("artists").get("items");
		var author = "Unknown Artist";
		if (artistsJson.isList() && !artistsJson.values().isEmpty()) {
			author = parseArtist(artistsJson);
		}

		String albumName = null;
		String albumUrl = null;
		String artworkUrl = null;
		var album = track.get("albumOfTrack");
		if (album != null && !album.isNull()) {
			albumName = album.get("name").safeText();
			var albumUri = album.get("uri").text();
			if (albumUri != null && !albumUri.isEmpty()) {
				albumUrl = "https://open.spotify.com/album/" + albumUri.replace("spotify:album:", "");
			}
			var sources = album.get("coverArt").get("sources");
			if (sources != null && sources.isList() && !sources.values().isEmpty()) {
				int size = sources.values().size();
				artworkUrl = sources.values().get(size - 1).get("url").text();
			}
		}

		String isrc = null;
		if (track.get("externalIds") != null && track.get("externalIds").get("isrc") != null) {
			isrc = track.get("externalIds").get("isrc").text();
		}
		if (isNullOrBlank(isrc) && !isNullOrBlank(identifier)) {
			isrc = this.fetchIsrcViaSpClientMetadata(identifier);
		}

		return new SpotifyAudioTrack(
			new AudioTrackInfo(title, author, preview ? PREVIEW_LENGTH : length, identifier, false, uri, artworkUrl, isrc),
			albumName,
			albumUrl,
			null,
			null,
			null,
			preview,
			sourceManager
		);
	}

	private AudioTrack parseTrackV2(JsonBrowser data, boolean preview, @Nullable String albumArtworkUrl, SpotifySourceManager sourceManager) {
		var trackData = data.get("itemV2").get("data");
		if (trackData.isNull()) {
			trackData = data;
		}

		var title = trackData.get("name").text();
		var length = trackData.get("trackDuration").get("totalMilliseconds").asLong(0);
		if (length == 0) {
			length = trackData.get("duration").get("totalMilliseconds").asLong(0);
		}
		if (length == 0) {
			length = trackData.get("duration_ms").asLong(0);
		}

		var author = trackData.get("artists").get("items").values().stream()
			.map(m -> m.get("profile").get("name").text())
			.filter(Objects::nonNull)
			.collect(Collectors.joining(", "));

		var spotifyUri = trackData.get("uri").text();
		var identifier = spotifyUri != null ? spotifyUri.replace("spotify:track:", "") : trackData.get("id").text();
		var uri = "https://open.spotify.com/track/" + identifier;

		var albumName = trackData.get("albumOfTrack").get("name").text();
		var albumUri = trackData.get("albumOfTrack").get("uri").text();
		var albumUrl = albumUri != null ? "https://open.spotify.com/album/" + albumUri.replace("spotify:album:", "") : null;

		var artworkUrl = albumArtworkUrl;
		if (artworkUrl == null) {
			var sources = trackData.get("albumOfTrack").get("coverArt").get("sources");
			if (sources.isList() && !sources.values().isEmpty()) {
				int size = sources.values().size();
				artworkUrl = sources.values().get(size - 1).get("url").text();
			}
		}

		var isrc = trackData.get("externalIds").get("isrc").text();
		if (isNullOrBlank(isrc)) {
			isrc = trackData.get("external_ids").get("isrc").text();
		}
		if (isNullOrBlank(isrc) && !isNullOrBlank(identifier)) {
			isrc = this.fetchIsrcViaSpClientMetadata(identifier);
		}

		return new SpotifyAudioTrack(
			new AudioTrackInfo(title, author, length, identifier, false, uri, artworkUrl, isrc),
			albumName,
			albumUrl,
			null,
			null,
			null,
			preview,
			sourceManager
		);
	}

	@NotNull
	private String parseArtist(JsonBrowser artistsJson) {
		var author = artistsJson.values().stream()
			.map(a -> {
				var name = a.get("profile").get("name").text();
				return (name != null && !name.isEmpty()) ? name : null;
			})
			.filter(Objects::nonNull)
			.collect(Collectors.joining(", "));

		return author.isEmpty() ? "Unknown Artist" : author;
	}

	@Nullable
	public String fetchIsrcViaSpClientMetadata(@NotNull String trackId) {
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
			var json = LavaSrcTools.fetchResponseAsJson(this.httpInterface, request);
			return parseIsrcFromSpClientMetadata(json);
		} catch (Exception e) {
			log.debug("Failed to fetch ISRC via spclient metadata: {}", e.getMessage());
			return null;
		}
	}

	private static boolean isNullOrBlank(@Nullable String value) {
		return value == null || value.trim().isEmpty();
	}

	@Nullable
	private static String parseIsrcFromSpClientMetadata(@Nullable JsonBrowser json) {
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

	/**
	 * Converts Spotify base62 id to 16-byte hex gid used by Spotify internal APIs.
	 */
	private static String spotifyIdToHex(@NotNull String base62Id) {
		final String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
		BigInteger value = BigInteger.ZERO;
		BigInteger base = BigInteger.valueOf(62);
		for (int i = 0; i < base62Id.length(); i++) {
			int idx = alphabet.indexOf(base62Id.charAt(i));
			if (idx < 0) {
				throw new IllegalArgumentException("Invalid base62 char in Spotify id: " + base62Id.charAt(i));
			}
			value = value.multiply(base).add(BigInteger.valueOf(idx));
		}
		String hex = value.toString(16);
		if (hex.length() > 32) {
			hex = hex.substring(hex.length() - 32);
		}
		return "0".repeat(32 - hex.length()) + hex;
	}
}
