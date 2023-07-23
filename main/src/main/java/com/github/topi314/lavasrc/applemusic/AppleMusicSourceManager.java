package com.github.topi314.lavasrc.applemusic;

import com.github.topi314.lavasearch.SearchSourceManager;
import com.github.topi314.lavasearch.protocol.*;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;

import java.io.DataInput;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AppleMusicSourceManager extends MirroringAudioSourceManager implements SearchSourceManager {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?music\\.apple\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>album|playlist|artist|song)(/[a-zA-Z\\d\\-]+)?/(?<identifier>[a-zA-Z\\d\\-.]+)(\\?i=(?<identifier2>\\d+))?");
	public static final Pattern TOKEN_SCRIPT_PATTERN = Pattern.compile("const \\w{2}=\"(?<token>(ey[\\w-]+)\\.([\\w-]+)\\.([\\w-]+))\"");
	public static final String SEARCH_PREFIX = "amsearch:";
	public static final String PREVIEW_PREFIX = "amprev:";
	public static final long PREVIEW_LENGTH = 30000;
	public static final int MAX_PAGE_ITEMS = 300;
	public static final String API_BASE = "https://api.music.apple.com/v1/";
	private final String countryCode;
	private int playlistPageLimit;
	private int albumPageLimit;
	private String token;
	private String origin;
	private Instant tokenExpire;

	public AppleMusicSourceManager(String[] providers, String mediaAPIToken, String countryCode, AudioPlayerManager audioPlayerManager) {
		this(mediaAPIToken, countryCode, unused -> audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public AppleMusicSourceManager(String[] providers, String mediaAPIToken, String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager) {
		this(mediaAPIToken, countryCode, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public AppleMusicSourceManager(String mediaAPIToken, String countryCode, AudioPlayerManager audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		this(mediaAPIToken, countryCode, unused -> audioPlayerManager, mirroringAudioTrackResolver);
	}

	public AppleMusicSourceManager(String mediaAPIToken, String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		super(audioPlayerManager, mirroringAudioTrackResolver);
		this.token = mediaAPIToken;
		try {
			this.parseTokenData();
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot parse token for expire date and origin", e);
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
				extendedAudioTrackInfo.artistArtworkUrl,
				extendedAudioTrackInfo.previewUrl,
				extendedAudioTrackInfo.isPreview,
				this
		);
	}

	@Override
	public @Nullable SearchResult loadSearch(@NotNull String query, @NotNull Set<SearchType> types) {
		try {
			if (query.startsWith(SEARCH_PREFIX)) {
				return getSearchSuggestions(query, types);
			}
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
		return null;
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
		if (this.token == null || this.token.isEmpty()) {
			return;
		}
		var json = JsonBrowser.parse(new String(Base64.getDecoder().decode(this.token.split("\\.")[1])));
		this.tokenExpire = Instant.ofEpochSecond(json.get("exp").asLong(0));
		this.origin = json.get("root_https_origin").index(0).text();
	}

	public void requestToken() throws IOException {
		var request = new HttpGet("https://music.apple.com");
		try (var response = this.httpInterfaceManager.getInterface().execute(request)) {
			var document = Jsoup.parse(response.getEntity().getContent(), null, "");
			var elements = document.select("script[type=module][src~=/assets/index.*.js]");
			if (elements.isEmpty()) {
				throw new IllegalStateException("Cannot find token script element");
			}

			for (var element : elements) {
				var tokenScriptURL = element.attr("src");
				request = new HttpGet("https://music.apple.com" + tokenScriptURL);
				try (var indexResponse = this.httpInterfaceManager.getInterface().execute(request)) {
					var tokenScript = IOUtils.toString(indexResponse.getEntity().getContent(), StandardCharsets.UTF_8);
					var tokenMatcher = TOKEN_SCRIPT_PATTERN.matcher(tokenScript);
					if (tokenMatcher.find()) {
						this.token = tokenMatcher.group("token");
						this.parseTokenData();
						return;
					}
				}

			}
		}
		throw new IllegalStateException("Cannot find token script url");
	}

	public String getToken() throws IOException {
		if (this.token == null || this.tokenExpire == null || this.tokenExpire.isBefore(Instant.now())) {
			this.requestToken();
		}
		return this.token;
	}

	public SearchResult getSearchSuggestions(String query, Set<SearchType> types) throws IOException, URISyntaxException {
		var urlBuilder = new URIBuilder(API_BASE + "catalog/" + countryCode + "/search/suggestions");
		urlBuilder.setParameter("term", query.substring(SEARCH_PREFIX.length()));
		urlBuilder.setParameter("kinds", "terms,topResults");
		var typesString = SearchTypeUtil.buildAppleMusicTypes(types);
		if (typesString.length() > 1) {
			urlBuilder.setParameter("types", typesString);
		}
		var json = getJson(urlBuilder.build().toString());

		var allSuggestions = json.get("results").get("suggestions");
		var terms = new ArrayList<SearchText>();
		var albums = new ArrayList<SearchAlbum>();
		var artists = new ArrayList<SearchArtist>();
		var playLists = new ArrayList<SearchPlaylist>();
		var tracks = new ArrayList<SearchTrack>();
		for (var term : allSuggestions.values()) {
			var kind = term.get("kind").text();
			if (kind.equals("terms")) {
				terms.add(new SearchText(term.get("searchTerm").text()));
			} else {
				var content = term.get("content");
				var type = content.get("type").text();
				var id = content.get("id").text();
				var attributes = content.get("attributes");
				var url = attributes.get("url").text();

				switch (type) {
					case "albums": {
						var name = attributes.get("name").text();
						var artist = attributes.get("artistName").text();
						var artworkUrl = parseArtworkUrl(attributes.get("artwork"));
						var trackCount = (int) attributes.get("trackCount").asLong(-1);
						var album = new SearchAlbum(
								id,
								name,
								artist,
								url,
								trackCount,
								artworkUrl,
								null
						);
						albums.add(album);
						break;
					}
					case "artists": {
						var name = attributes.get("name").text();
						var artworkUrl = parseArtworkUrl(attributes.get("artwork"));
						var artist = new SearchArtist(id, name, url, artworkUrl);
						artists.add(artist);
						break;
					}
					case "playlists": {
						var name = attributes.get("name").text();
						var artworkUrl = parseArtworkUrl(attributes.get("artwork"));
						var trackCount = (int) attributes.get("trackCount").asLong(-1);
						var playlist = new SearchPlaylist(id, name, url, artworkUrl, trackCount);
						playLists.add(playlist);
						break;
					}
					case "songs": {
						var name = attributes.get("name").text();
						var artworkUrl = parseArtworkUrl(attributes.get("artwork"));
						var isrc = attributes.get("isrc").text();
						var author = attributes.get("artistName").text();
						var length = attributes.get("durationInMillis").asLong(0);
						var track = new SearchTrack(name, author, length, id, false, url, artworkUrl, isrc);
						tracks.add(track);
						break;
					}
				}
			}
		}

		return new SearchResult(albums, artists, playLists, tracks, terms);
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
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/songs/" + id);
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
		return new AppleMusicAudioTrack(
			new AudioTrackInfo(
				attributes.get("name").text(),
				attributes.get("artistName").text(),
				preview ? PREVIEW_LENGTH : attributes.get("durationInMillis").asLong(0),
				json.get("id").text(),
				false,
				attributes.get("url").text(),
				this.parseArtworkUrl(attributes.get("artwork")),
				attributes.get("isrc").text()
			),
			attributes.get("albumName").text(),
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
