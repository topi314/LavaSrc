package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifySourceManager
  extends MirroringAudioSourceManager
  implements HttpConfigurable, AudioSearchManager {

  public static final Pattern URL_PATTERN = Pattern.compile(
    "(https?://)(www\\.)?open\\.spotify\\.com/((?<region>[a-zA-Z-]+)/)?(user/(?<user>[a-zA-Z0-9-_]+)/)?(?<type>track|album|playlist|artist)/(?<identifier>[a-zA-Z0-9-_]+)"
  );
  public static final String SEARCH_PREFIX = "spsearch:";
  public static final String RECOMMENDATIONS_PREFIX = "sprec:";
  public static final String PREVIEW_PREFIX = "spprev:";
  public static final long PREVIEW_LENGTH = 30000;
  public static final String SHARE_URL = "https://spotify.link/";
  public static final int PLAYLIST_MAX_PAGE_ITEMS = 100;
  public static final int ALBUM_MAX_PAGE_ITEMS = 50;
  public static final String API_BASE = "https://api.spotify.com/v1/";
  public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(
    AudioSearchResult.Type.ALBUM,
    AudioSearchResult.Type.ARTIST,
    AudioSearchResult.Type.PLAYLIST,
    AudioSearchResult.Type.TRACK
  );
  private static final Logger log = LoggerFactory.getLogger(
    SpotifySourceManager.class
  );

  private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
  private final String countryCode;
  private int playlistPageLimit = 6;
  private int albumPageLimit = 6;
  private String token;
  private Instant tokenExpire;

  public SpotifySourceManager(
    String[] providers,
    String countryCode,
    AudioPlayerManager audioPlayerManager
  ) {
    this(
      countryCode,
      audioPlayerManager,
      new DefaultMirroringAudioTrackResolver(providers)
    );
  }

  public SpotifySourceManager(
    String[] providers,
    String countryCode,
    Function<Void, AudioPlayerManager> audioPlayerManager
  ) {
    this(
      countryCode,
      audioPlayerManager,
      new DefaultMirroringAudioTrackResolver(providers)
    );
  }

  public SpotifySourceManager(
    String countryCode,
    AudioPlayerManager audioPlayerManager,
    MirroringAudioTrackResolver mirroringAudioTrackResolver
  ) {
    this(
      countryCode,
      unused -> audioPlayerManager,
      mirroringAudioTrackResolver
    );
  }

  public SpotifySourceManager(
    String countryCode,
    Function<Void, AudioPlayerManager> audioPlayerManager,
    MirroringAudioTrackResolver mirroringAudioTrackResolver
  ) {
    super(audioPlayerManager, mirroringAudioTrackResolver);
    if (countryCode == null || countryCode.isEmpty()) {
      countryCode = "US";
    }
    this.countryCode = countryCode;
  }

  public void setPlaylistPageLimit(int playlistPageLimit) {
    this.playlistPageLimit = playlistPageLimit;
  }

  public void setAlbumPageLimit(int albumPageLimit) {
    this.albumPageLimit = albumPageLimit;
  }

  @Override
  public String getSourceName() {
    return "spotify";
  }

  @Override
  public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input)
    throws IOException {
    var extendedAudioTrackInfo = super.decodeTrack(input);
    return new SpotifyAudioTrack(
      trackInfo,
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
  public AudioSearchResult loadSearch(
    @NotNull String query,
    @NotNull Set<AudioSearchResult.Type> types
  ) {
    try {
      if (query.startsWith(SEARCH_PREFIX)) {
        return this.getAutocomplete(
            query.substring(SEARCH_PREFIX.length()),
            types
          );
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  @Override
  public AudioItem loadItem(
    AudioPlayerManager manager,
    AudioReference reference
  ) {
    var identifier = reference.identifier;
    var preview = reference.identifier.startsWith(PREVIEW_PREFIX);
    return this.loadItem(
        preview ? identifier.substring(PREVIEW_PREFIX.length()) : identifier,
        preview
      );
  }

  public AudioItem loadItem(String identifier, boolean preview) {
    try {
      if (identifier.startsWith(SEARCH_PREFIX)) {
        return this.getSearch(
            identifier.substring(SEARCH_PREFIX.length()).trim(),
            preview
          );
      }

      if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
        return this.getRecommendations(
            identifier.substring(RECOMMENDATIONS_PREFIX.length()).trim(),
            preview
          );
      }

      // If the identifier is a share URL, we need to follow the redirect to find out the real url behind it
      if (identifier.startsWith(SHARE_URL)) {
        var request = new HttpHead(identifier);
        request.setConfig(
          RequestConfig.custom().setRedirectsEnabled(false).build()
        );
        try (
          var response = this.httpInterfaceManager.getInterface()
            .execute(request)
        ) {
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

  public void obtainAccessToken() throws IOException {
    var accessTokenUrl =
      "https://open.spotify.com/get_access_token?reason=transport&productType=embed";
    var request = new HttpGet(accessTokenUrl);
    var json = HttpClientTools.fetchResponseAsJson(
      this.httpInterfaceManager.getInterface(),
      request
    );

    this.token = json.get("accessToken").text();
    var expirationTimestampMs = json
      .get("accessTokenExpirationTimestampMs")
      .asLong(0);
    this.tokenExpire = Instant.ofEpochMilli(expirationTimestampMs);
  }

  public String getToken() throws IOException {
    if (
      this.token == null ||
      this.tokenExpire == null ||
      this.tokenExpire.isBefore(Instant.now())
    ) {
      this.obtainAccessToken();
    }
    return this.token;
  }

  public JsonBrowser getJson(String uri) throws IOException {
    var request = new HttpGet(uri);
    request.addHeader("Authorization", "Bearer " + this.getToken());
    return LavaSrcTools.fetchResponseAsJson(
      this.httpInterfaceManager.getInterface(),
      request
    );
  }

private AudioSearchResult getAutocomplete(String query, Set<AudioSearchResult.Type> types) throws IOException {
    if (types.isEmpty()) {
        types = SEARCH_TYPES;
    }
    var url = API_BASE + "search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=" +
            types.stream().map(AudioSearchResult.Type::getName).collect(Collectors.joining(","));
    var json = getJson(url);
    if (json == null) {
        return AudioSearchResult.EMPTY;
    }

    var tracks = parseTrackItems(json.get("tracks"), false);
    var albums = parsePlaylistItems(json.get("albums"), ExtendedAudioPlaylist.Type.ALBUM);
    var artists = parsePlaylistItems(json.get("artists"), ExtendedAudioPlaylist.Type.ARTIST);
    var playlists = parsePlaylistItems(json.get("playlists"), ExtendedAudioPlaylist.Type.PLAYLIST);

    return new BasicAudioSearchResult(tracks, albums, artists, playlists, new ArrayList<>());
}

private List<AudioPlaylist> parsePlaylistItems(JsonBrowser items, ExtendedAudioPlaylist.Type type) {
    var playlists = new ArrayList<AudioPlaylist>();
    for (var playlist : items.get("items").values()) {
        playlists.add(new SpotifyAudioPlaylist(
                playlist.get("name").text(),
                Collections.emptyList(),
                type,
                playlist.get("external_urls").get("spotify").text(),
                playlist.get("images").index(0).get("url").text(),
                playlist.get("owner").get("display_name").text(),
                type == ExtendedAudioPlaylist.Type.PLAYLIST ? (int) playlist.get("tracks").get("total").asLong(0) : null
        ));
    }
    return playlists;
}

  public AudioItem getSearch(String query, boolean preview) throws IOException {
    var json =
      this.getJson(
          API_BASE +
          "search?q=" +
          URLEncoder.encode(query, StandardCharsets.UTF_8) +
          "&type=track"
        );
    if (json == null || json.get("tracks").get("items").values().isEmpty()) {
      return AudioReference.NO_TRACK;
    }

    var artistIds = json
      .get("tracks")
      .get("items")
      .values()
      .stream()
      .map(track -> track.get("artists").index(0).get("id").text())
      .collect(Collectors.joining(","));
    var artistJson = this.getJson(API_BASE + "artists?ids=" + artistIds);
    if (artistJson != null) {
      for (var artist : artistJson.get("artists").values()) {
        for (var track : json.get("tracks").get("items").values()) {
          if (
            track
              .get("artists")
              .index(0)
              .get("id")
              .text()
              .equals(artist.get("id").text())
          ) {
            track.get("artists").index(0).put("images", artist.get("images"));
          }
        }
      }
    }

    return new BasicAudioPlaylist(
      "Search results for: " + query,
      this.parseTrackItems(json.get("tracks"), preview),
      null,
      true
    );
  }

  public AudioItem getRecommendations(String query, boolean preview)
    throws IOException {
    var json = this.getJson(API_BASE + "recommendations?" + query);
    if (json == null || json.get("tracks").values().isEmpty()) {
      return AudioReference.NO_TRACK;
    }

    return new SpotifyAudioPlaylist(
      "Spotify Recommendations:",
      this.parseTracks(json, preview),
      ExtendedAudioPlaylist.Type.RECOMMENDATIONS,
      null,
      null,
      null,
      null
    );
  }

  public AudioItem getAlbum(String id, boolean preview) throws IOException {
    var json = this.getJson(API_BASE + "albums/" + id);
    if (json == null) {
      return AudioReference.NO_TRACK;
    }

    var artistJson =
      this.getJson(
          API_BASE + "artists/" + json.get("artists").index(0).get("id").text()
        );
    if (artistJson == null) {
      artistJson = JsonBrowser.newMap();
    }

    var tracks = new ArrayList<AudioTrack>();
    JsonBrowser page;
    var offset = 0;
    var pages = 0;
    do {
      page =
        this.getJson(
            API_BASE +
            "albums/" +
            id +
            "/tracks?limit=" +
            ALBUM_MAX_PAGE_ITEMS +
            "&offset=" +
            offset
          );
      offset += ALBUM_MAX_PAGE_ITEMS;

      var tracksPage =
        this.getJson(
            API_BASE +
            "tracks/?ids=" +
            page
              .get("items")
              .values()
              .stream()
              .map(track -> track.get("id").text())
              .collect(Collectors.joining(","))
          );

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

    return new SpotifyAudioPlaylist(
      json.get("name").text(),
      tracks,
      ExtendedAudioPlaylist.Type.ALBUM,
      json.get("external_urls").get("spotify").text(),
      json.get("images").index(0).get("url").text(),
      json.get("artists").index(0).get("name").text(),
      (int) json.get("total_tracks").asLong(0)
    );
  }

  public AudioItem getPlaylist(String id, boolean preview) throws IOException {
    var json = this.getJson(API_BASE + "playlists/" + id);
    if (json == null) {
      return AudioReference.NO_TRACK;
    }

    var tracks = new ArrayList<AudioTrack>();
    JsonBrowser page;
    var offset = 0;
    var pages = 0;
    do {
      page =
        this.getJson(
            API_BASE +
            "playlists/" +
            id +
            "/tracks?limit=" +
            PLAYLIST_MAX_PAGE_ITEMS +
            "&offset=" +
            offset
          );
      offset += PLAYLIST_MAX_PAGE_ITEMS;

      for (var value : page.get("items").values()) {
        var track = value.get("track");
        if (track.isNull() || track.get("is_local").asBoolean(false) || track.get("type").text().equals("episode")) { // https://github.com/topi314/LavaSrc/commit/d35f8934fe66b8a453dba1d09d6a1374e9ce8fed 
          continue;
        }
        tracks.add(this.parseTrack(track, preview));
      }
    } while (
      page.get("next").text() != null && ++pages < this.playlistPageLimit
    );

    if (tracks.isEmpty()) {
      return AudioReference.NO_TRACK;
    }

    return new SpotifyAudioPlaylist(
      json.get("name").text(),
      tracks,
      ExtendedAudioPlaylist.Type.PLAYLIST,
      json.get("external_urls").get("spotify").text(),
      json.get("images").index(0).get("url").text(),
      json.get("owner").get("display_name").text(),
      (int) json.get("tracks").get("total").asLong(0)
    );
  }

  public AudioItem getArtist(String id, boolean preview) throws IOException {
    var json = this.getJson(API_BASE + "artists/" + id);
    if (json == null) {
      return AudioReference.NO_TRACK;
    }

    var tracksJson =
      this.getJson(
          API_BASE + "artists/" + id + "/top-tracks?market=" + this.countryCode
        );
    if (tracksJson == null || tracksJson.get("tracks").values().isEmpty()) {
      return AudioReference.NO_TRACK;
    }

    for (var track : tracksJson.get("tracks").values()) {
      track.get("artists").index(0).put("images", json.get("images"));
    }

    return new SpotifyAudioPlaylist(
      json.get("name").text() + "'s Top Tracks",
      this.parseTracks(tracksJson, preview),
      ExtendedAudioPlaylist.Type.ARTIST,
      json.get("external_urls").get("spotify").text(),
      json.get("images").index(0).get("url").text(),
      json.get("name").text(),
      (int) tracksJson.get("tracks").get("total").asLong(0)
    );
  }

  public AudioItem getTrack(String id, boolean preview) throws IOException {
    var json = this.getJson(API_BASE + "tracks/" + id);
    if (json == null) {
      return AudioReference.NO_TRACK;
    }

    var artistJson =
      this.getJson(
          API_BASE + "artists/" + json.get("artists").index(0).get("id").text()
        );
    if (artistJson != null) {
      json.get("artists").index(0).put("images", artistJson.get("images"));
    }

    return this.parseTrack(json, preview);
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
var artistsArray = json.get("artists");
List<String> artistNamesList = new ArrayList<>();
for (int i = 0; i < artistsArray.values().size(); i++) {
    var currentArtistName = artistsArray.index(i).get("name").text();
    artistNamesList.add(currentArtistName);
}
var artistName = String.join(", ", artistNamesList);


    AudioTrackInfo trackInfo = new AudioTrackInfo(
        json.get("name").text(),
        artistName,
        preview ? PREVIEW_LENGTH : json.get("duration_ms").asLong(0),
        json.get("id").text(),
        false,
        json.get("external_urls").get("spotify").text(),
        json.get("album").get("images").index(0).get("url").text(),
        json.get("external_ids").get("isrc").text()
    );

    return new SpotifyAudioTrack(
        trackInfo,
        json.get("album").get("name").text(),
        json.get("album").get("external_urls").get("spotify").text(),
        artistsArray.index(0).get("external_urls").get("spotify").text(),
        artistsArray.index(0).get("images").index(0).get("url").text(),
        json.get("preview_url").text(),
        preview,
        this
    );
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
  public void configureRequests(
    Function<RequestConfig, RequestConfig> configurator
  ) {
    this.httpInterfaceManager.configureRequests(configurator);
  }

  @Override
  public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
    this.httpInterfaceManager.configureBuilder(configurator);
  }
}
