package com.github.topi314.lavasrc.applemusic;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.AudioText;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioText;
import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AppleMusicSourceManager extends MirroringAudioSourceManager implements AudioSearchManager {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?music\\.apple\\.com/((?<countrycode>[a-zA-Z]{2})/)?(?<type>album|playlist|artist|song)(/[a-zA-Z\\p{L}\\d\\-]+)?/(?<identifier>[a-zA-Z\\d\\-.]+)(\\?i=(?<identifier2>\\d+))?");
	public static final String SEARCH_PREFIX = "amsearch:";
	public static final String PREVIEW_PREFIX = "amprev:";
	public static final long PREVIEW_LENGTH = 30000;
	public static final int MAX_PAGE_ITEMS = 300;
	public static final String API_BASE = "https://api.music.apple.com/v1/";
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK, AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.ARTIST, AudioSearchResult.Type.TEXT);
	public static final Set<AudioSearchResult.Type> TOP_RESULT_SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK, AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.ARTIST);

	private final String countryCode;
	private int playlistPageLimit;
	private int albumPageLimit;
	private final String token;
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

	@NotNull
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
	public @Nullable AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
		try {
			if (query.startsWith(SEARCH_PREFIX)) {
				return this.getSearchSuggestions(query.substring(SEARCH_PREFIX.length()), types);
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

		return this.loadItem(preview ? identifier.substring(PREVIEW_PREFIX.length()) : identifier, preview);
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

	public AudioSearchResult getSearchSuggestions(String query, Set<AudioSearchResult.Type> types) throws IOException, URISyntaxException {
		if (types.isEmpty()) {
			types = SEARCH_TYPES;
		}

		var urlBuilder = new URIBuilder(API_BASE + "catalog/" + countryCode + "/search/suggestions");
		urlBuilder.setParameter("term", query);
		urlBuilder.setParameter("extend", "artistUrl");
		var kinds = new HashSet<String>();
		if (types.contains(AudioSearchResult.Type.TEXT)) {
			kinds.add("terms");
		}
		for (var type : types) {
			if (TOP_RESULT_SEARCH_TYPES.contains(type)) {
				kinds.add("topResults");
				break;
			}
		}
		urlBuilder.setParameter("kinds", String.join(",", kinds));
		var typesString = SearchTypeUtil.buildAppleMusicTypes(types);
		if (!typesString.isEmpty()) {
			urlBuilder.setParameter("types", typesString);
		}
		var json = getJson(urlBuilder.build().toString());

		var allSuggestions = json.get("results").get("suggestions");
		var terms = new ArrayList<AudioText>();
		var albums = new ArrayList<AudioPlaylist>();
		var artists = new ArrayList<AudioPlaylist>();
		var playLists = new ArrayList<AudioPlaylist>();
		var tracks = new ArrayList<AudioTrack>();
		for (var term : allSuggestions.values()) {
			var kind = term.get("kind").text();
			if (kind.equals("terms")) {
				terms.add(new BasicAudioText(term.get("searchTerm").text()));
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
						var trackCount = (int) attributes.get("trackCount").asLong(0);
						var album = new AppleMusicAudioPlaylist(name, Collections.emptyList(), ExtendedAudioPlaylist.Type.ALBUM, url, artworkUrl, artist, trackCount);
						albums.add(album);
						break;
					}
					case "artists": {
						var name = attributes.get("name").text();
						var artworkUrl = parseArtworkUrl(attributes.get("artwork"));
						var artist = new AppleMusicAudioPlaylist(name + "'s Top Tracks", Collections.emptyList(), ExtendedAudioPlaylist.Type.ARTIST, url, artworkUrl, name, null);
						artists.add(artist);
						break;
					}
					case "playlists": {
						var name = attributes.get("name").text();
						var artworkUrl = parseArtworkUrl(attributes.get("artwork"));
						var trackCount = (int) attributes.get("trackCount").asLong(0);
						var author = attributes.get("data").index(0).get("attributes").get("curatorName").text();
						var playlist = new AppleMusicAudioPlaylist(name, Collections.emptyList(), ExtendedAudioPlaylist.Type.PLAYLIST, url, artworkUrl, author, trackCount);
						playLists.add(playlist);
						break;
					}
					case "songs": {
						var name = attributes.get("name").text();
						var artworkUrl = parseArtworkUrl(attributes.get("artwork"));
						var isrc = attributes.get("isrc").text();
						var author = attributes.get("artistName").text();
						var length = attributes.get("durationInMillis").asLong(0);
						var albumName = attributes.get("albumName").text();
						var artistUrl = attributes.get("artistUrl").text();
						var albumUrl = url.substring(0, url.indexOf('?'));
						var previewUrl = attributes.get("previews").index(0).get("url").text();
						var info = new AudioTrackInfo(
							name,
							author,
							length,
							id,
							false,
							url,
							artworkUrl,
							isrc
						);
						var track = new AppleMusicAudioTrack(info, albumName, albumUrl, artistUrl, null, previewUrl, false, this);
						tracks.add(track);
						break;
					}
				}
			}
		}

		return new BasicAudioSearchResult(tracks, albums, artists, playLists, terms);
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.addHeader("Authorization", "Bearer " + this.getToken());
		if (this.origin != null && !this.origin.isEmpty()) {
			request.addHeader("Origin", "https://" + this.origin);
		}
		return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	public Map<String, String> getArtistCover(List<String> ids) throws IOException {
		if (ids.isEmpty()) {
			return Map.of();
		}
		var json = getJson(API_BASE + "catalog/" + countryCode + "/artists?ids=" + String.join(",", ids));
		var output = new HashMap<String, String>(ids.size());
		for (var i = 0; i < ids.size(); i++) {
			var artist = json.get("data").index(i);
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
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/albums/" + id + "?extend=artistUrl");
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

			page.get("data").values().forEach(tracksRaw::add);
		}
		while (page.get("next").text() != null && ++pages < albumPageLimit);

		var tracks = parseTrackList(tracksRaw, preview);
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = this.parseArtworkUrl(json.get("data").index(0).get("attributes").get("artwork"));
		var author = json.get("data").index(0).get("attributes").get("artistName").text();
		return new AppleMusicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, ExtendedAudioPlaylist.Type.ALBUM, json.get("data").index(0).get("attributes").get("url").text(), artworkUrl, author, (int) json.get("data").index(0).get("attributes").get("trackCount").asLong(0));
	}

	public AudioItem getPlaylist(String id, String countryCode, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var tracksRaw = JsonBrowser.newList();
		JsonBrowser page;
		var offset = 0;
		var pages = 0;
		do {
			page = this.getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + id + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=" + offset + "&extend=artistUrl");
			offset += MAX_PAGE_ITEMS;

			page.get("data").values().forEach(tracksRaw::add);
		}
		while (page.get("next").text() != null && ++pages < playlistPageLimit);

		var tracks = parseTrackList(tracksRaw, preview);
		if (tracks.isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var artworkUrl = this.parseArtworkUrl(json.get("data").index(0).get("attributes").get("artwork"));
		var author = json.get("data").index(0).get("attributes").get("curatorName").text();
		return new AppleMusicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, ExtendedAudioPlaylist.Type.PLAYLIST, json.get("data").index(0).get("attributes").get("url").text(), artworkUrl, author, (int) json.get("data").index(0).get("attributes").get("trackCount").asLong(0));
	}

	public AudioItem getArtist(String id, String countryCode, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/artists/" + id + "/view/top-songs");
		if (json == null || json.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var jsonArtist = this.getJson(API_BASE + "catalog/" + countryCode + "/artists/" + id);

		var author = jsonArtist.get("data").index(0).get("attributes").get("name").text();

		var artworkUrl = this.parseArtworkUrl(jsonArtist.get("data").index(0).get("attributes").get("artwork"));
		var artistArtwork = new HashMap<String, String>();
		if (artworkUrl != null) {
			artistArtwork.put(jsonArtist.get("data").index(0).get("id").text(), artworkUrl);
		}
		var tracks = parseTracks(json, preview, artistArtwork);
		return new AppleMusicAudioPlaylist(author + "'s Top Tracks", tracks, ExtendedAudioPlaylist.Type.ARTIST, json.get("data").index(0).get("attributes").get("url").text(), artworkUrl, author, tracks.size());
	}

	public AudioItem getSong(String id, String countryCode, boolean preview) throws IOException {
		var json = this.getJson(API_BASE + "catalog/" + countryCode + "/songs/" + id + "?extend=artistUrl");
		if (json == null) {
			return AudioReference.NO_TRACK;
		}

		var artistId = this.parseArtistId(json);
		String artistArtwork = null;
		if (artistId != null) {
			artistArtwork = getArtistCover(List.of(artistId)).values().iterator().next();
		}
		return parseTrack(json.get("data").index(0), preview, artistArtwork);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview, Map<String, String> artistArtwork) {
		var tracks = new ArrayList<AudioTrack>();
		for (var value : json.get("data").values()) {
			var artistId = this.parseArtistId(value);
			String artworkUrl = null;
			if (artistId != null) {
				artworkUrl = artistArtwork.get(artistId);
			}
			tracks.add(this.parseTrack(value, preview, artworkUrl));
		}
		return tracks;
	}

	private List<AudioTrack> parseTrackList(JsonBrowser json, boolean preview) throws IOException {
		var jsonData = JsonBrowser.newMap();
		jsonData.put("data", json);
		return parseTracks(jsonData, preview);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json, boolean preview) throws IOException {
		var ids = json.get("data").values().stream().map(this::parseArtistId).filter(Predicate.not(Objects::isNull)).collect(Collectors.toList());
		return parseTracks(json, preview, getArtistCover(ids));
	}

	private AudioTrack parseTrack(JsonBrowser json, boolean preview, String artistArtwork) {
		var attributes = json.get("attributes");
		var trackUrl = attributes.get("url").text();
		var artistUrl = json.get("artistUrl").text();
		if (artistUrl != null && (artistUrl.isEmpty() || artistUrl.startsWith("https://music.apple.com/WebObjects/MZStore.woa/wa/viewCollaboration"))) {
			artistUrl = null;
		}
		return new AppleMusicAudioTrack(
			new AudioTrackInfo(
				attributes.get("name").text(),
				attributes.get("artistName").text(),
				preview ? PREVIEW_LENGTH : attributes.get("durationInMillis").asLong(0),
				json.get("id").text(),
				false,
				trackUrl,
				this.parseArtworkUrl(attributes.get("artwork")),
				attributes.get("isrc").text()
			),
			attributes.get("albumName").text(),
			// Apple doesn't give us the album url, however the track url is
			// /albums/{albumId}?i={trackId}, so if we cut off that parameter it's fine
			trackUrl.substring(0, trackUrl.indexOf('?')),
			artistUrl,
			artistArtwork,
			attributes.get("previews").index(0).get("hlsUrl").text(),
			preview,
			this
		);
	}

	private String parseArtworkUrl(JsonBrowser json) {
		var text = json.get("url").text();
		if (text == null) {
			return null;
		}
		return text.replace("{w}", json.get("width").text()).replace("{h}", json.get("height").text());
	}

	@Nullable
	private String parseArtistId(JsonBrowser json) {
		var url = json.get("data").index(0).get("attributes").get("artistUrl").text();
		if (url == null || url.isEmpty()) {
			return null;
		}
		if (url.startsWith("https://music.apple.com/WebObjects/MZStore.woa/wa/viewCollaboration")) {
			return null;
		}
		return url.substring(url.lastIndexOf('/') + 1);
	}


	public static AppleMusicSourceManager fromMusicKitKey(String musicKitKey, String keyId, String teamId, String countryCode, AudioPlayerManager audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) throws NoSuchAlgorithmException, InvalidKeySpecException {
		var base64 = musicKitKey.replaceAll("-----BEGIN PRIVATE KEY-----\n", "")
			.replaceAll("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		var keyBytes = Base64.getDecoder().decode(base64);
		var spec = new PKCS8EncodedKeySpec(keyBytes);
		var keyFactory = KeyFactory.getInstance("EC");
		var key = (ECKey) keyFactory.generatePrivate(spec);
		var jwt = JWT.create()
			.withIssuer(teamId)
			.withIssuedAt(Instant.now())
			.withExpiresAt(Instant.now().plus(Duration.ofSeconds(15777000)))
			.withKeyId(keyId)
			.sign(Algorithm.ECDSA256(key));
		return new AppleMusicSourceManager(jwt, countryCode, audioPlayerManager, mirroringAudioTrackResolver);
	}

}
