package com.github.topi314.lavasrc.tidal;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
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

public class TidalSourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

	public static final Pattern URL_PATTERN = Pattern.compile("https?://(?:(?:listen|www)\\.)?tidal\\.com/(?:browse/)?(?<type>album|track|playlist|mix)/(?<id>[a-zA-Z0-9\\-]+)(?:/.*)?(?:\\?.*)?");
	public static final String SEARCH_PREFIX = "tdsearch:";
	public static final String RECOMMENDATIONS_PREFIX = "tdrec:";
	public static final String PUBLIC_API_BASE = "https://api.tidal.com/v1/";
	public static final int PLAYLIST_MAX_PAGE_ITEMS = 750;
	public static final int ALBUM_MAX_PAGE_ITEMS = 120;

	private static final String USER_AGENT = "TIDAL/3704 CFNetwork/1220.1 Darwin/20.3.0";
	private static final Logger log = LoggerFactory.getLogger(TidalSourceManager.class);

	private final String tidalToken;
	private final HttpInterfaceManager httpInterfaceManager;
	private final String countryCode;
	private int searchLimit = 6;

	public TidalSourceManager(String[] providers, String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager, String tidalToken) {
		this(countryCode, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers), tidalToken);
	}

	public TidalSourceManager(String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver, String tidalToken) {
		super(audioPlayerManager, mirroringAudioTrackResolver);
		this.countryCode = (countryCode == null || countryCode.isEmpty()) ? "US" : countryCode;
		if (tidalToken == null || tidalToken.isEmpty()) {
			throw new IllegalArgumentException("Tidal token must be provided");
		}
		this.tidalToken = tidalToken;
		this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
	}

	public void setSearchLimit(int searchLimit) {
		this.searchLimit = searchLimit;
	}

	@Override
	public String getSourceName() {
		return "tidal";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new TidalAudioTrack(trackInfo, extendedAudioTrackInfo.albumName, extendedAudioTrackInfo.albumUrl, extendedAudioTrackInfo.artistUrl, extendedAudioTrackInfo.previewUrl, this);
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		try {
			var matcher = URL_PATTERN.matcher(reference.identifier);
			if (matcher.matches()) {
				var type = matcher.group("type");
				var id = matcher.group("id");

				switch (type) {
					case "album":
						return this.getAlbumOrPlaylist(id, "album", ALBUM_MAX_PAGE_ITEMS);
					case "mix":
						return this.getMix(id);
					case "track":
						return this.getTrack(id);
					case "playlist":
						return this.getAlbumOrPlaylist(id, "playlist", PLAYLIST_MAX_PAGE_ITEMS);
					default:
						return null;
				}
			}

			if (reference.identifier.startsWith(SEARCH_PREFIX)) {
				var query = reference.identifier.substring(SEARCH_PREFIX.length());
				if (query.isEmpty()) {
					throw new IllegalArgumentException("No query provided for search");
				}
				return this.getSearch(query);
			}

			if (reference.identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
				var trackId = reference.identifier.substring(RECOMMENDATIONS_PREFIX.length());
				if (trackId.isEmpty()) {
					throw new IllegalArgumentException("No track ID provided for recommendations");
				}
				return this.getRecommendations(trackId);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	private JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("user-agent", USER_AGENT);
		request.setHeader("x-tidal-token", this.tidalToken);
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var audio : json.values()) {
			var parsedTrack = this.parseTrack(audio);
			if (parsedTrack != null) {
				tracks.add(parsedTrack);
			}
		}
		return tracks;
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&offset=0&limit=" + searchLimit + "&countryCode=" + countryCode);
		if (json.get("tracks").get("items").isNull()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(json.get("tracks").get("items"));
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Tidal Search: " + query, tracks, null, true);
	}

	private AudioItem getRecommendations(String trackId) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "tracks/" + trackId + "?countryCode=" + countryCode);
		if (json.isNull()) {
			return AudioReference.NO_TRACK;
		}

		var mixId = json.get("mixes").get("TRACK_MIX").text();
		if (mixId == null) {
			return AudioReference.NO_TRACK;
		}

		return this.getMix(mixId);
	}

	private AudioTrack parseTrack(JsonBrowser audio) {
		var id = audio.get("id").text();
		var duration = audio.get("duration").asLong(0) * 1000;
		if (duration == 0) {
			return null;
		}
		var title = audio.get("title").text();
		var originalUrl = audio.get("url").text();
		var artistName = audio.get("artists").values().stream().map(artist -> artist.get("name").text()).collect(Collectors.joining(", "));

		var coverIdentifier = audio.get("album").get("cover").text();
		String artworkUrl;
		if (coverIdentifier == null) {
			artworkUrl = "https://tidal.com/_nuxt/img/logos.d8ce10b.jpg";
		} else {
			artworkUrl = "https://resources.tidal.com/images/" + coverIdentifier.replaceAll("-", "/") + "/1280x1280.jpg";
		}
		var isrc = audio.get("isrc").text();
		return new TidalAudioTrack(new AudioTrackInfo(title, artistName, duration, id, false, originalUrl, artworkUrl, isrc), this);
	}

	private AudioItem getAlbumOrPlaylist(String itemId, String type, int maxPageItems) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + type + "s/" + itemId + "/tracks?countryCode=" + countryCode + "&limit=" + maxPageItems);
		if (json == null || json.get("items").isNull()) {
			return AudioReference.NO_TRACK;
		}

		var items = this.parseTrackItem(json);
		if (items.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		String itemInfoUrl;
		var trackType = type.equalsIgnoreCase("playlist") ? ExtendedAudioPlaylist.Type.PLAYLIST : ExtendedAudioPlaylist.Type.ALBUM;
		if (trackType == ExtendedAudioPlaylist.Type.PLAYLIST) {
			itemInfoUrl = PUBLIC_API_BASE + "playlists/" + itemId + "?countryCode=" + countryCode;
		} else {
			itemInfoUrl = PUBLIC_API_BASE + "albums/" + itemId + "?countryCode=" + countryCode;
		}

		var itemInfoJson = this.getJson(itemInfoUrl);
		if (itemInfoJson == null) {
			return AudioReference.NO_TRACK;
		}

		String title;
		String artistName;
		String url;
		String coverUrl;
		long totalTracks;

		if (trackType == ExtendedAudioPlaylist.Type.PLAYLIST) {
			title = itemInfoJson.get("title").text();
			url = itemInfoJson.get("url").text();
			coverUrl = itemInfoJson.get("squareImage").text();
			artistName = itemInfoJson.get("promotedArtists").index(0).get("name").text();
			totalTracks = itemInfoJson.get("numberOfTracks").asLong(0);
		} else {
			title = itemInfoJson.get("title").text();
			url = itemInfoJson.get("url").text();
			coverUrl = itemInfoJson.get("cover").text();
			artistName = itemInfoJson.get("artists").index(0).get("name").text();
			totalTracks = itemInfoJson.get("numberOfTracks").asLong(0);
		}
		if (title == null || url == null) {
			return AudioReference.NO_TRACK;
		}
		var artworkUrl = "https://resources.tidal.com/images/" + coverUrl.replaceAll("-", "/") + "/1080x1080.jpg";
		return new TidalAudioPlaylist(title, items, type.equalsIgnoreCase("playlist") ? ExtendedAudioPlaylist.Type.PLAYLIST : ExtendedAudioPlaylist.Type.ALBUM, url, artworkUrl, artistName, (int) totalTracks);
	}

	public AudioItem getTrack(String trackId) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "tracks/" + trackId + "?countryCode=" + countryCode);
		if (json == null || json.isNull()) {
			return AudioReference.NO_TRACK;
		}

		var track = this.parseTrack(json);
		if (track == null) {
			return AudioReference.NO_TRACK;
		}

		return track;
	}

	public AudioItem getMix(String mixId) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "mixes/" + mixId + "/items?countryCode=" + countryCode);
		if (json == null || json.get("items").isNull()) {
			return AudioReference.NO_TRACK;
		}

		var items = this.parseTrackItem(json);
		if (items.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		return new BasicAudioPlaylist("Mix: " + mixId, items, null, false);
	}

	private List<AudioTrack> parseTrackItem(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var audio : json.get("items").values()) {
			var parsedTrack = this.parseTrack(audio.get("item").isNull() ? audio : audio.get("item"));
			if (parsedTrack != null) {
				tracks.add(parsedTrack);
			}
		}
		return tracks;
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		httpInterfaceManager.configureBuilder(configurator);
	}

	@Override
	public void shutdown() {
		try {
			httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	public HttpInterface getHttpInterface() {
		return httpInterfaceManager.getInterface();
	}
}
