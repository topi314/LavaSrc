package com.github.topi314.lavasrc.applemusic;

import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.methods.HttpGet;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AppleMusicSourceManager extends MirroringAudioSourceManager {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?music\\.apple\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>album|playlist|artist|song)(/[a-zA-Z\\d\\-]+)?/(?<identifier>[a-zA-Z\\d\\-.]+)(\\?i=(?<identifier2>\\d+))?");
	public static final String SEARCH_PREFIX = "amsearch:";
	public static final String PREVIEW_PREFIX = "amprev:";
	public static final long PREVIEW_LENGTH = 30000;
	public static final int MAX_PAGE_ITEMS = 300;
	public static final String API_BASE = "https://api.music.apple.com/v1/";
	private final String countryCode;
	private int playlistPageLimit;
	private int albumPageLimit;
	private final String token;
	private String origin;
	private Instant tokenExpire;

	public AppleMusicSourceManager(String[] providers, String mediaAPIToken, String countryCode, AudioPlayerManager audioPlayerManager) {
		this(mediaAPIToken, countryCode, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public AppleMusicSourceManager(String mediaAPIToken, String countryCode, AudioPlayerManager audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		super(audioPlayerManager, mirroringAudioTrackResolver);
		if (mediaAPIToken == null || mediaAPIToken.isEmpty()) {
			throw new RuntimeException("Apple Music API token is empty or null");
		}
		this.token = mediaAPIToken;

		try {
			this.parseTokenData();
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse Apple Music API token", e);
		}

		if (countryCode == null || countryCode.isEmpty()) {
			this.countryCode = "us";
		} else {
			this.countryCode = countryCode;
		}
	}

	public void setPlaylistPageLimit(int playlistPageLimit) {
		this.playlistPageLimit = playlistPageLimit;
	}

	public void setAlbumPageLimit(int albumPageLimit) {
		this.albumPageLimit = albumPageLimit;
	}

	@Override
	public String getSourceName() {
		return "applemusic";
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		var extendedAudioTrackInfo = super.decodeTrack(input);
		return new AppleMusicAudioTrack(trackInfo,
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

		return loadItem(preview ? identifier.substring(PREVIEW_PREFIX.length()) : identifier, preview);
	}

	public AudioItem loadItem(String identifier, boolean preview) {
		try {
			if (identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(identifier.substring(SEARCH_PREFIX.length()).trim(), preview);
			}

			var matcher = URL_PATTERN.matcher(identifier);
			if (!matcher.find()) {
				return null;
			}

			var countryCode = matcher.group("countrycode");
			var id = matcher.group("identifier");
			switch (matcher.group("type")) {
				case "song":
					return this.getSong(id, countryCode, preview);

				case "album":
					var id2 = matcher.group("identifier2");
					if (id2 == null || id2.isEmpty()) {
						return this.getAlbum(id, countryCode, preview);
					}
					return this.getSong(id2, countryCode, preview);

				case "playlist":
					return this.getPlaylist(id, countryCode, preview);

				case "artist":
					return this.getArtist(id, countryCode, preview);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	public void parseTokenData() throws IOException {
		var json = JsonBrowser.parse(new String(Base64.getDecoder().decode(this.token.split("\\.")[1])));
		this.tokenExpire = Instant.ofEpochSecond(json.get("exp").asLong(0));
		this.origin = json.get("root_https_origin").index(0).text();
	}

	public String getToken() throws IOException {
		if (this.tokenExpire.isBefore(Instant.now())) {
			throw new FriendlyException("Apple Music API token is expired", FriendlyException.Severity.SUSPICIOUS, null);
		}
		return this.token;
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.addHeader("Authorization", "Bearer " + this.getToken());
		if (this.origin != null && !this.origin.isEmpty()) {
			request.addHeader("Origin", "https://" + this.origin);
		}
		return HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	public Map<String, String> getArtistCover(List<String> ids) throws IOException {
		var json = getJson(API_BASE + "catalog/" + countryCode + "/artists?ids=" + String.join(",", ids));
		var data = json.get("data");

		var output = new HashMap<String, String>(ids.size());
		for (var i = 0; i < ids.size(); i++) {
			var artist = data.index(i);
			var artwork = artist.get("attributes").get("artwork");
			output.put(artist.get("id").text(), parseArtworkUrl(artwork));
		}

		return output;
	}

	public AudioItem getSearch(String query, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=" + 25 + "&extend=artistUrl");
		if (json == null || json.get("results").get("songs").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new BasicAudioPlaylist("Apple Music Search: " + query, this.parseTracks(json.get("results").get("songs"), preview), null, true);
	}

	public AudioItem getAlbum(String id, String countryCode, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/albums/" + id + "&extend=artistUrl");
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracksRaw = JsonBrowser.newList();
		JsonBrowser page;
		var offset = 0;
		var pages = 0;
		do {
			page = this.getJson(API_BASE + "catalog/" + countryCode + "/albums/" + id + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=" + offset);
			offset += MAX_PAGE_ITEMS;

			page.values().forEach(tracksRaw::add);
		}
		while (page.get("next").text() != null && ++pages < albumPageLimit);

		var tracks = parseTracks(tracksRaw, preview);
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = this.parseArtworkUrl(json.get("data").index(0).get("attributes").get("artwork"));
		var author = json.get("data").index(0).get("attributes").get("artistName").text();
		return new AppleMusicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, "album", json.get("data").index(0).get("attributes").get("url").text(), artworkUrl, author);
	}

	public AudioItem getPlaylist(String id, String countryCode, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + id + "&extend=artistUrl");
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracksRaw = JsonBrowser.newList();
		JsonBrowser page;
		var offset = 0;
		var pages = 0;
		do {
			page = this.getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + id + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=" + offset);
			offset += MAX_PAGE_ITEMS;

			page.values().forEach(tracksRaw::add);
		}
		while (page.get("next").text() != null && ++pages < playlistPageLimit);

		var tracks = parseTracks(tracksRaw, preview);
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = this.parseArtworkUrl(json.get("data").index(0).get("attributes").get("artwork"));
		var author = json.get("data").index(0).get("attributes").get("curatorName").text();
		return new AppleMusicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, "playlist", json.get("data").index(0).get("attributes").get("url").text(), artworkUrl, author);
	}

	public AudioItem getArtist(String id, String countryCode, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/artists/" + id + "/view/top-songs");
		if (json == null || json.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var jsonArtist = this.getJson(API_BASE + "catalog/" + countryCode + "/artists/" + id);

		var artworkUrl = this.parseArtworkUrl(jsonArtist.get("data").index(0).get("attributes").get("artwork"));
		var author = jsonArtist.get("data").index(0).get("attributes").get("name").text();
		var artistArtwork = Map.of(jsonArtist.get("id").text(), artworkUrl);
		return new AppleMusicAudioPlaylist(author + "'s Top Tracks", parseTracks(json, preview, artistArtwork), "artist", json.get("data").index(0).get("attributes").get("url").text(), artworkUrl, author);
	}

	public AudioItem getSong(String id, String countryCode, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/songs/" + id + "?extend=artistUrl");
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		var artistArtwork = getArtistCover(List.of(parseArtistId(json))).values().iterator().next();
		return parseTrack(json.get("data").index(0), preview, artistArtwork);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview, Map<String, String> artistArtwork) {
		var tracks = new ArrayList<AudioTrack>();
		for (var value : json.get("data").values()) {
			tracks.add(this.parseTrack(value, preview, artistArtwork.get(parseArtistId(value))));
		}
		return tracks;
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview) throws IOException {
		var ids = json.get("data").values().stream().map(this::parseArtistId).collect(Collectors.toList());
		return parseTracks(json, preview, getArtistCover(ids));
	}

	private AudioTrack parseTrack(JsonBrowser json, boolean preview, String artistArtwork) {
		var attributes = json.get("attributes");
		var artistUrl = attributes.get("url").text();
		return new AppleMusicAudioTrack(
				new AudioTrackInfo(
						attributes.get("name").text(),
						attributes.get("artistName").text(),
						preview ? PREVIEW_LENGTH : attributes.get("durationInMillis").asLong(0),
						json.get("id").text(),
						false,
						artistUrl,
						this.parseArtworkUrl(attributes.get("artwork")),
						attributes.get("isrc").text()
				),
				attributes.get("albumName").text(),
				// Apple doesn't give us the album url, however the track url is
				// /albums/{albumId}?i={trackId}, so if we cut off that parameter it's fine
				artistUrl.substring(0, artistUrl.indexOf('?')),
				attributes.get("artistUrl").text(),
				artistArtwork,
				attributes.get("previews").index(0).get("hlsUrl").text(),
				preview,
				this
		);
	}

	private String parseArtworkUrl(JsonBrowser json) {
		return json.get("url").text().replace("{w}", json.get("width").text()).replace("{h}", json.get("height").text());
	}

	private String parseArtistId(JsonBrowser json) {
		var url = json.get("data").index(0).get("attributes").get("artistUrl").text();
		return url.substring(url.lastIndexOf('/'));
	}

}
