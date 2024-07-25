package com.github.topi314.lavasrc.vkmusic;

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
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class VkMusicSourceManager extends ExtendedAudioSourceManager implements HttpConfigurable {
	private static final Pattern VK_PLAYLIST_HEADER_REGEX = Pattern.compile("z=audio_playlist(?<owner>-?[A-Za-z\\d]+)_(?<id>-?[A-Za-z\\d]+)(?<accessKey>_([^/?#]*))?(?:[/?#].*)?");
	private static final Pattern VK_PLAYLIST_TYPE_REGEX = Pattern.compile("(https?://)?(?:www\\.)?vk\\.(?:com|ru)/music/(playlist|album)/(?<owner>-?[A-Za-z\\d]+)_(?<id>-?[A-Za-z\\d]+)(?<accessKey>_([^/?#]*))?(?:[/?#].*)?");
	private static final Pattern VK_TRACK_REGEX = Pattern.compile("(https?://)?(?:www\\.)?vk\\.(?:com|ru)/audio(?<id>-?\\d+)_(?<artistId>-?\\d+)(?<accessKey>_([^/?#]*))?(?:[/?#].*)?");
	private static final Pattern VK_ARTIST_REGEX = Pattern.compile("(https?://)?(?:www\\.)?vk\\.(?:com|ru)/artist/(?<artistId>[^/?#]+)");

	public static final String SEARCH_PREFIX = "vksearch:";
	public static final String RECOMMENDATIONS_PREFIX = "vkrec:";
	public static final String PUBLIC_API_BASE = "https://api.vk.com/method/";
	public static final String API_VERSION = "5.199";

	private static final Logger log = LoggerFactory.getLogger(VkMusicSourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager;

	private final String userToken;
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

	public void setArtistLoadLimit(int artistLimit) {
		this.artistLoadLimit = artistLimit;
	}

	public void setPlaylistLoadLimit(int playlistLimit) {
		this.playlistLoadLimit = playlistLimit;
	}

	public void setRecommendationsLoadLimit(int recommendationsLimit) {
        this.recommendationsLoadLimit = recommendationsLimit;
    }

	@Override
	public String getSourceName() {
		return "vkmusic";
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

			var uri = URLDecoder.decode(reference.identifier, StandardCharsets.UTF_8);

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
		var json = this.getJson("audio.getRecommendations",  "&target_audio=" + audioId + "&count=" + this.recommendationsLoadLimit);
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
		var playlistJson = this.getJson(
				"audio.getPlaylistById",
				query + "&playlist_id=" + playlist_id
			)
			.get("response");

		if (!playlistJson.isNull()) {
			if (!playlistJson.get("title").isNull()) {
				title = playlistJson.get("title").text();
			}
			if (!playlistJson.get("photo").isNull()) {
				coverUri = playlistJson.get("photo").get("photo_600").text();
			}
		}

		var type = ExtendedAudioPlaylist.Type.PLAYLIST;
		if (playlistJson.get("type").as(Long.class) == 1) {
			type = ExtendedAudioPlaylist.Type.ALBUM;
		}

		return new VkMusicAudioPlaylist(
			title,
			tracks,
			type,
			"https://vk.com/music/playlist/" + owner_id + "_" + playlist_id,
			coverUri,
			null,
			tracks.size()
		);
	}

	private AudioItem getTrack(String id) throws IOException {
		var json = this.getJson("audio.getById", "&audios=" + id);
		if (json.isNull() || json.get("response").isNull()) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json.get("response").values().get(0));
	}

	private AudioItem getArtist(String id) throws IOException {
		var json = this.getJson("audio.getAudiosByArtist", "&artist_id=" + id + "&count" + artistLoadLimit * 20);
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
		var uri = PUBLIC_API_BASE + method + "?v=" + API_VERSION + headers;
		if (!this.userToken.isEmpty()) {
			uri += "&access_token=" + this.userToken;
		}

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
			if (json.get("url").isNull()) {
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
