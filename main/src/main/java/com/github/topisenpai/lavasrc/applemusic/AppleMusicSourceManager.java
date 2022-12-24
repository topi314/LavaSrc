package com.github.topisenpai.lavasrc.applemusic;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

public class AppleMusicSourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

    public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?music\\.apple\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>album|playlist|artist|song)(/[a-zA-Z\\d\\-]+)?/(?<identifier>[a-zA-Z\\d\\-.]+)(\\?i=(?<identifier2>\\d+))?");
    public static final Pattern TOKEN_SCRIPT_PATTERN = Pattern.compile("const \\w{2}=\"(?<token>(ey[\\w-]+)\\.([\\w-]+)\\.([\\w-]+))\"");
    public static final String SEARCH_PREFIX = "amsearch:";
    public static final int MAX_PAGE_ITEMS = 300;
    public static final String API_BASE = "https://api.music.apple.com/v1/";
    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    private final String countryCode;
    private String token;
    private String origin;
    private Instant tokenExpire;

    public AppleMusicSourceManager(String[] providers, String mediaAPIToken, String countryCode, AudioPlayerManager audioPlayerManager) {
        super(providers, audioPlayerManager);
        token = mediaAPIToken;
        try {
            parseTokenData();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse token for expire date and origin", e);
        }
        if (countryCode == null || countryCode.isEmpty()) {
            this.countryCode = "us";
        } else {
            this.countryCode = countryCode;
        }
    }

    @Override
    public String getSourceName() {
        return "applemusic";
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new AppleMusicAudioTrack(trackInfo,
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
            Matcher matcher = URL_PATTERN.matcher(reference.identifier);
            if (!matcher.find()) return null;

            String countryCode = matcher.group("countrycode");
            String identifier = matcher.group("identifier");

            switch (matcher.group("type")) {
                case "song":
                    return getSong(identifier, countryCode);
                case "album":
                    String identifier2 = matcher.group("identifier2");
                    if (identifier2 == null || identifier2.isEmpty()) {
                        return getAlbum(identifier, countryCode);
                    }
                    return getSong(identifier2, countryCode);
                case "playlist":
                    return getPlaylist(identifier, countryCode);
                case "artist":
                    return getArtist(identifier, countryCode);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public void parseTokenData() throws IOException {
        if (token == null || token.isEmpty()) {
            return;
        }
        JsonBrowser json = JsonBrowser.parse(new String(Base64.getDecoder().decode(token.split("\\.")[1])));
        tokenExpire = Instant.ofEpochSecond(json.get("exp").asLong(0));
        origin = json.get("root_https_origin").index(0).text();
    }

    public void requestToken() throws IOException {
        HttpGet request = new HttpGet("https://music.apple.com");
        try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(request)) {
            Document document = Jsoup.parse(response.getEntity().getContent(), null, "");
            Elements elements = document.select("script[type=module][src~=/assets/index.*.js]");
            if (elements.isEmpty()) throw new IllegalStateException("Cannot find token script element");

            for (Element element : elements) {
                String tokenScriptURL = element.attr("src");
                request = new HttpGet("https://music.apple.com" + tokenScriptURL);
                try (CloseableHttpResponse indexResponse = httpInterfaceManager.getInterface().execute(request)) {
                    String tokenScript = IOUtils.toString(indexResponse.getEntity().getContent(), StandardCharsets.UTF_8);
                    Matcher tokenMatcher = TOKEN_SCRIPT_PATTERN.matcher(tokenScript);
                    if (tokenMatcher.find()) {
                        token = tokenMatcher.group("token");
                        parseTokenData();
                        return;
                    }
                }

            }
        }
        throw new IllegalStateException("Cannot find token script url");
    }

    public String getToken() throws IOException {
        if (token == null || tokenExpire == null || tokenExpire.isBefore(Instant.now())) requestToken();
        return token;
    }

    public JsonBrowser getJson(String uri) throws IOException {
        HttpGet request = new HttpGet(uri);
        request.addHeader("Authorization", "Bearer " + getToken());
        if (origin != null && !origin.isEmpty()) request.addHeader("Origin", "https://" + origin);
        return HttpClientTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), request);
    }

    public AudioItem getFirstSearchResultAsTrack(String query) throws IOException {
        List<AudioTrack> searchResults = getSearchResults(query);
        return searchResults.isEmpty() ? AudioReference.NO_TRACK : searchResults.get(0);
    }

    public AudioItem getAllSearchResultsAsPlaylist(String query) throws IOException {
        List<AudioTrack> searchResults = getSearchResults(query);
        return searchResults.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist("Apple Music Search Results For: " + query, searchResults, null, true);
    }

    private List<AudioTrack> getSearchResults(String query) throws IOException {
        JsonBrowser json = getJson(API_BASE + "catalog/" + countryCode + "/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=" + 25);
        return json == null || json.get("results").get("songs").get("data").values().isEmpty() ? Collections.emptyList() : parseTracks(json.get("results").get("songs"));
    }

    public AudioItem getAlbum(String identifier, String countryCode) throws IOException {
        JsonBrowser json = getJson(API_BASE + "catalog/" + countryCode + "/albums/" + identifier);
        if (json == null) return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        int offset = 0;
        JsonBrowser page = getJson(API_BASE + "catalog/" + countryCode + "/albums/" + identifier + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=0");

        while (page.get("next").text() != null) {
            offset += MAX_PAGE_ITEMS;
            tracks.addAll(parseTracks(page));
            page = getJson(API_BASE + "catalog/" + countryCode + "/albums/" + identifier + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=" + offset);
        }

        return tracks.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, null, false);
    }

    public AudioItem getPlaylist(String identifier, String countryCode) throws IOException {
        JsonBrowser json = getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + identifier);
        if (json == null) return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        int offset = 0;
        JsonBrowser page = getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + identifier + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=0");

        while(page.get("next").text() != null) {
            offset += MAX_PAGE_ITEMS;
            tracks.addAll(parseTracks(page));
            page = getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + identifier + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=" + offset);
        }

        return tracks.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, null, false);
    }

    public AudioItem getArtist(String identifier, String countryCode) throws IOException {
        JsonBrowser json = getJson(API_BASE + "catalog/" + countryCode + "/artists/" + identifier + "/view/top-songs");
        return json == null || json.get("data").values().isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist(json.get("data").index(0).get("attributes").get("artistName").text() + "'s Top Tracks", parseTracks(json), null, false);
    }

    public AudioItem getSong(String identifier, String countryCode) throws IOException {
        JsonBrowser json = getJson(API_BASE + "catalog/" + countryCode + "/songs/" + identifier);
        return json == null ? AudioReference.NO_TRACK : parseTrack(json.get("data").index(0));
    }

    private List<AudioTrack> parseTracks(JsonBrowser json) {
        return json.get("data").values().stream()
                .map(this::parseTrack)
                .collect(Collectors.toList());
    }

    private AudioTrack parseTrack(JsonBrowser json) {
        JsonBrowser attributes = json.get("attributes");
        JsonBrowser artwork = attributes.get("artwork");
        return new AppleMusicAudioTrack(
                new AudioTrackInfo(
                        attributes.get("name").text(),
                        attributes.get("artistName").text(),
                        attributes.get("durationInMillis").asLong(0),
                        json.get("id").text(),
                        false,
                        attributes.get("url").text()
                ),
                attributes.get("isrc").text(),
                artwork.get("url").text().replace("{w}", artwork.get("width").text()).replace("{h}", artwork.get("height").text()),
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
