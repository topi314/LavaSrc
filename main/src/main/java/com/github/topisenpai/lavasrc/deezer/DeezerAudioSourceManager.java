package com.github.topisenpai.lavasrc.deezer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeezerAudioSourceManager implements AudioSourceManager, HttpConfigurable {

    public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?deezer\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>track|album|playlist|artist)/(?<identifier>[0-9]+)");
    public static final String SEARCH_PREFIX = "dzsearch:";
    public static final String ISRC_PREFIX = "dzisrc:";
    public static final String SHARE_URL = "https://deezer.page.link/";
    public static final String PUBLIC_API_BASE = "https://api.deezer.com/2.0";
    public static final String PRIVATE_API_BASE = "https://www.deezer.com/ajax/gw-light.php";
    public static final String MEDIA_BASE = "https://media.deezer.com/v1";

    private final String masterDecryptionKey;

    private final HttpInterfaceManager httpInterfaceManager;

    public DeezerAudioSourceManager(String masterDecryptionKey) {
        if (masterDecryptionKey == null || masterDecryptionKey.isEmpty()) {
            throw new IllegalArgumentException("Deezer master key must be set");
        }
        this.masterDecryptionKey = masterDecryptionKey;
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "deezer";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            if (reference.identifier.startsWith(SEARCH_PREFIX)) {
                return getFirstSearchResultAsTrack(reference.identifier.substring(SEARCH_PREFIX.length()));
            }

            if (reference.identifier.startsWith(ISRC_PREFIX)) {
                return getTrackByISRC(reference.identifier.substring(ISRC_PREFIX.length()));
            }

            // If the identifier is a share URL, we need to follow the redirect to find out the real url behind it
            if (reference.identifier.startsWith(SHARE_URL)) {
                HttpGet request = new HttpGet(reference.identifier);
                request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
                try (CloseableHttpResponse response = this.httpInterfaceManager.getInterface().execute(request)) {
                    if (response.getStatusLine().getStatusCode() == 302) {
                        String location = response.getFirstHeader("Location").getValue();
                        if (location.startsWith("https://www.deezer.com/")) {
                            return loadItem(manager, new AudioReference(location, reference.title));
                        }
                    }
                    return AudioReference.NO_TRACK;
                }
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

    public JsonBrowser getJson(String uri) throws IOException {
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", "application/json");
        return HttpClientTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), request);
    }

    private List<AudioTrack> parseTracks(JsonBrowser json) {
        return json.get("data").values().stream()
                .filter(track -> track.get("type").text().equals("track"))
                .map(this::parseTrack)
                .collect(Collectors.toList());
    }

    private AudioTrack parseTrack(JsonBrowser json) {
        String id = json.get("id").text();
        return new DeezerAudioTrack(new AudioTrackInfo(
                json.get("title").text(),
                json.get("artist").get("name").text(),
                json.get("duration").as(Long.class) * 1000,
                id,
                false,
                "https://deezer.com/track/" + id),
                json.get("isrc").text(),
                json.get("album").get("cover_xl").text(),
                this
        );
    }

    public AudioItem getTrackByISRC(String isrc) throws IOException {
        JsonBrowser json = getJson(PUBLIC_API_BASE + "/track/isrc:" + isrc);
        return json == null || json.get("id").isNull() ? AudioReference.NO_TRACK : parseTrack(json);
    }

    public AudioItem getFirstSearchResultAsTrack(String query) throws IOException {
        List<AudioTrack> searchResults = getSearchResults(query);
        return searchResults.isEmpty() ? AudioReference.NO_TRACK : searchResults.get(0);
    }

    public AudioItem getAllSearchResultsAsPlaylist(String query) throws IOException {
        List<AudioTrack> searchResults = getSearchResults(query);
        return searchResults.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist("Deezer Search Results For: " + query, searchResults, null, true);
    }

    private List<AudioTrack> getSearchResults(String query) throws IOException {
        JsonBrowser json = getJson(PUBLIC_API_BASE + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        return json == null || json.get("data").values().isEmpty() ? Collections.emptyList() : parseTracks(json);
    }

    public AudioItem getAlbum(String identifier) throws IOException {
        JsonBrowser json = getJson(PUBLIC_API_BASE + "/album/" + identifier);
        return json == null || json.get("tracks").get("data").values().isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("title").text(), parseTracks(json.get("tracks")), null, false);
    }

    public AudioItem getTrack(String identifier) throws IOException {
        JsonBrowser json = this.getJson(PUBLIC_API_BASE + "/track/" + identifier);
        return json == null ? AudioReference.NO_TRACK : parseTrack(json);
    }

    public AudioItem getPlaylist(String identifier) throws IOException {
        JsonBrowser json = getJson(PUBLIC_API_BASE + "/playlist/" + identifier);
        return json == null || json.get("tracks").get("data").values().isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("title").text(), parseTracks(json.get("tracks")), null, false);
    }

    public AudioItem getArtist(String identifier) throws IOException {
        JsonBrowser json = this.getJson(PUBLIC_API_BASE + "/artist/" + identifier + "/top?limit=50");
        return json == null || json.get("data").values().isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("data").index(0).get("artist").get("name").text() + "'s Top Tracks", parseTracks(json), null, false);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        DeezerAudioTrack deezerAudioTrack = (DeezerAudioTrack) track;
        DataFormatTools.writeNullableText(output, deezerAudioTrack.getISRC());
        DataFormatTools.writeNullableText(output, deezerAudioTrack.getArtworkURL());
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new DeezerAudioTrack(trackInfo,
                DataFormatTools.readNullableText(input),
                DataFormatTools.readNullableText(input),
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

    public String getMasterDecryptionKey() {
        return masterDecryptionKey;
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

}
