package com.github.topisenpai.lavasrc.spotify;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpotifySourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

    public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?open\\.spotify\\.com/(user/[a-zA-Z0-9-_]+/)?(?<type>track|album|playlist|artist)/(?<identifier>[a-zA-Z0-9-_]+)");
    public static final String SEARCH_PREFIX = "spsearch:";
    public static final String RECOMMENDATIONS_PREFIX = "sprec:";
    public static final int PLAYLIST_MAX_PAGE_ITEMS = 100;
    public static final int ALBUM_MAX_PAGE_ITEMS = 50;
    public static final String API_BASE = "https://api.spotify.com/v1/";

    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    private final String clientId;
    private final String clientSecret;
    private final String countryCode;
    private String token;
    private Instant tokenExpire;

    public SpotifySourceManager(String[] providers, String clientId, String clientSecret, String countryCode, AudioPlayerManager audioPlayerManager) {
        super(providers, audioPlayerManager);

        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalArgumentException("Spotify client id must be set");
        }
        this.clientId = clientId;

        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalArgumentException("Spotify secret must be set");
        }
        this.clientSecret = clientSecret;

        if (countryCode == null || countryCode.isEmpty()) {
            countryCode = "US";
        }
        this.countryCode = countryCode;
    }

    @Override
    public String getSourceName() {
        return "spotify";
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new SpotifyAudioTrack(trackInfo,
                DataFormatTools.readNullableText(input),
                DataFormatTools.readNullableText(input),
                this
        );
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            if (reference.identifier.startsWith(SEARCH_PREFIX)) {
                return getFirstSearchResultAsTrack(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
            }
            if (reference.identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
                return getAllRecommendationResultsAsPlaylist(reference.identifier.substring(RECOMMENDATIONS_PREFIX.length()).trim());
            }

            Matcher matcher = URL_PATTERN.matcher(reference.identifier);
            if (!matcher.find()) return null;


            String identifier = matcher.group("identifier");
            return switch (matcher.group("type")) {
                case "track" -> getTrack(identifier);
                case "playlist" -> getPlaylist(identifier);
                case "album" -> getAlbum(identifier);
                case "artist" -> getArtist(identifier);
                default -> throw new IllegalArgumentException();
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void requestToken() throws IOException {
        HttpPost request = new HttpPost("https://accounts.spotify.com/api/token");
        request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)));
        request.setEntity(new UrlEncodedFormEntity(List.of(new BasicNameValuePair("grant_type", "client_credentials")), StandardCharsets.UTF_8));

        JsonBrowser json = HttpClientTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), request);
        token = json.get("access_token").text();
        tokenExpire = Instant.now().plusSeconds(json.get("expires_in").asLong(0));
    }

    public String getToken() throws IOException {
        if (token == null || tokenExpire == null || tokenExpire.isBefore(Instant.now())) {
            requestToken();
        }
        return token;
    }

    public JsonBrowser getJson(String uri) throws IOException {
        HttpGet request = new HttpGet(uri);
        request.addHeader("Authorization", "Bearer " + getToken());
        return HttpClientTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), request);
    }

    public AudioItem getFirstSearchResultAsTrack(String query) throws IOException {
        List<AudioTrack> searchResults = getSearchResults(query);
        return searchResults.isEmpty() ? AudioReference.NO_TRACK : searchResults.get(0);
    }

    public AudioItem getAllSearchResultsAsPlaylist(String query) throws IOException {
        List<AudioTrack> searchResults = getSearchResults(query);
        return searchResults.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist("Spotify Search Results For: " + query, searchResults, null, true);
    }

    private List<AudioTrack> getSearchResults(String query) throws IOException {
        JsonBrowser json = getJson(API_BASE + "search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=track");
        return json == null || json.get("tracks").get("items").values().isEmpty() ? Collections.emptyList() : parseTrackItems(json.get("tracks"));
    }

    public AudioItem getFirstRecommendationResultAsTrack(String query) throws IOException {
        List<AudioTrack> recommendationResults = getRecommendationResults(query);
        return recommendationResults.isEmpty() ? AudioReference.NO_TRACK : recommendationResults.get(0);
    }

    public AudioItem getAllRecommendationResultsAsPlaylist(String query) throws IOException{
        List<AudioTrack> recommendationResults = getRecommendationResults(query);
        return recommendationResults.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist("Recommendation Results For: " + query, recommendationResults, null, false);
    }

    private List<AudioTrack> getRecommendationResults(String query) throws IOException {
        JsonBrowser json = getJson(API_BASE + "recommendations?" + query);
        return json == null || json.get("tracks").values().isEmpty() ? Collections.emptyList() : parseTracks(json);
    }

    public AudioItem getAlbum(String identifier) throws IOException {
        JsonBrowser json = getJson(API_BASE + "albums/" + identifier);
        if (json == null) return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        int offset = 0;
        JsonBrowser page = getJson(API_BASE + "albums/" + identifier + "/tracks?limit=" + ALBUM_MAX_PAGE_ITEMS + "&offset=0");

        while (page.get("next").text() != null) {
            offset += ALBUM_MAX_PAGE_ITEMS;
            tracks.addAll(parseTrackItems(page));
            page = getJson(API_BASE + "albums/" + identifier + "/tracks?limit=" + ALBUM_MAX_PAGE_ITEMS + "&offset=" + offset);
        }

        return tracks.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("name").text(), tracks, null, false);
    }

    public AudioItem getPlaylist(String identifier) throws IOException {
        JsonBrowser json = getJson(API_BASE + "playlists/" + identifier);
        if (json == null) return AudioReference.NO_TRACK;


        List<AudioTrack> tracks = new ArrayList<>();
        int offset = 0;
        JsonBrowser page = getJson(API_BASE + "playlists/" + identifier + "/tracks?limit=" + PLAYLIST_MAX_PAGE_ITEMS + "&offset=0");

        //TODO This can be cleaned up functionally.
        while(page.get("next").text() != null) {
            offset += ALBUM_MAX_PAGE_ITEMS;
            page.get("items").values().forEach(value -> {
                JsonBrowser track = value.get("track");
                if (track.isNull() || track.get("is_local").asBoolean(false)) return;
                tracks.add(parseTrack(track));
            });

            page = getJson(API_BASE + "playlists/" + identifier + "/tracks?limit=" + PLAYLIST_MAX_PAGE_ITEMS + "&offset=" + offset);
        }

        return tracks.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("name").text(), tracks, null, false);
    }

    public AudioItem getArtist(String identifier) throws IOException {
        JsonBrowser json = this.getJson(API_BASE + "artists/" + identifier + "/top-tracks?market=" + countryCode);
        return json == null || json.get("tracks").values().isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("tracks").index(0).get("artists").index(0).get("name").text() + "'s Top Tracks", this.parseTracks(json), null, false);
    }

    public AudioItem getTrack(String id) throws IOException {
        var json = this.getJson(API_BASE + "tracks/" + id);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }
        return parseTrack(json);
    }

    private List<AudioTrack> parseTracks(JsonBrowser json) {
        return json.get("tracks").values().stream()
                .map(this::parseTrack)
                .collect(Collectors.toList());
    }

    private List<AudioTrack> parseTrackItems(JsonBrowser json) {
        return json.get("items").values().stream()
                .filter(item -> !item.isNull() && !item.get("is_local").asBoolean(false))
                .map(this::parseTrack)
                .collect(Collectors.toList());
    }

    private AudioTrack parseTrack(JsonBrowser json) {
        return new SpotifyAudioTrack(
                new AudioTrackInfo(
                        json.get("name").text(),
                        json.get("artists").index(0).get("name").text(),
                        json.get("duration_ms").asLong(0),
                        json.get("id").text(),
                        false,
                        json.get("external_urls").get("spotify").text()
                ),
                json.get("external_ids").get("isrc").text(),
                json.get("album").get("images").index(0).get("url").text(),
                this
        );
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

}
