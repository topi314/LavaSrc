package com.github.topisenpai.lavasrc.applemusic;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioTrackLookup;
import com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class AppleMusicSourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

    public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?music\\.apple\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>album|playlist|artist|song)(/[a-zA-Z\\d\\-]+)?/(?<identifier>[a-zA-Z\\d\\-.]+)(\\?i=(?<identifier2>\\d+))?");
    public static final Pattern TOKEN_SCRIPT_PATTERN = Pattern.compile("const \\w{2}=\"(?<token>(ey[\\w-]+)\\.([\\w-]+)\\.([\\w-]+))\"");
    public static final String SEARCH_PREFIX = "amsearch:";
    public static final int MAX_PAGE_ITEMS = 300;
    public static final String API_BASE = "https://api.music.apple.com/v1/";
    private static final Logger log = LoggerFactory.getLogger(AppleMusicSourceManager.class);
    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    private final String countryCode;
    private String token;
    private String origin;
    private Instant tokenExpire;

    public AppleMusicSourceManager(String[] providers, String mediaAPIToken, String countryCode, AudioPlayerManager audioPlayerManager) {
        this(mediaAPIToken, countryCode, audioPlayerManager, defaultMirroringAudioTrackLookup(providers));
    }

    public AppleMusicSourceManager(String mediaAPIToken, String countryCode, AudioPlayerManager audioPlayerManager, MirroringAudioTrackLookup mirroringAudioTrackLookup) {
        super(audioPlayerManager, mirroringAudioTrackLookup);
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
                return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
            }

            var matcher = URL_PATTERN.matcher(reference.identifier);
            if (!matcher.find()) {
                return null;
            }

            var countryCode = matcher.group("countrycode");
            var id = matcher.group("identifier");
            switch (matcher.group("type")) {
                case "song":
                    return this.getSong(id, countryCode);

                case "album":
                    var id2 = matcher.group("identifier2");
                    if (id2 == null || id2.isEmpty()) {
                        return this.getAlbum(id, countryCode);
                    }
                    return this.getSong(id2, countryCode);

                case "playlist":
                    return this.getPlaylist(id, countryCode);

                case "artist":
                    return this.getArtist(id, countryCode);
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

    public JsonBrowser getJson(String uri) throws IOException {
        var request = new HttpGet(uri);
        request.addHeader("Authorization", "Bearer " + this.getToken());
        if (this.origin != null && !this.origin.isEmpty()) {
            request.addHeader("Origin", "https://" + this.origin);
        }
        return HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
    }

    public AudioItem getSearch(String query) throws IOException {
        var json = this.getJson(API_BASE + "catalog/" + countryCode + "/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=" + 25);
        if (json == null || json.get("results").get("songs").get("data").values().isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        return new BasicAudioPlaylist("Apple Music Search: " + query, parseTracks(json.get("results").get("songs")), null, true);
    }

    public AudioItem getAlbum(String id, String countryCode) throws IOException {
        var json = this.getJson(API_BASE + "catalog/" + countryCode + "/albums/" + id);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }

        var tracks = new ArrayList<AudioTrack>();
        JsonBrowser page;
        var offset = 0;
        do {
            page = this.getJson(API_BASE + "catalog/" + countryCode + "/albums/" + id + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=" + offset);
            offset += MAX_PAGE_ITEMS;

            tracks.addAll(parseTracks(page));
        }
        while (page.get("next").text() != null);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, null, false);
    }

    public AudioItem getPlaylist(String id, String countryCode) throws IOException {
        var json = this.getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + id);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }

        var tracks = new ArrayList<AudioTrack>();
        JsonBrowser page;
        var offset = 0;
        do {
            page = this.getJson(API_BASE + "catalog/" + countryCode + "/playlists/" + id + "/tracks?limit=" + MAX_PAGE_ITEMS + "&offset=" + offset);
            offset += MAX_PAGE_ITEMS;

            tracks.addAll(parseTracks(page));
        }
        while (page.get("next").text() != null);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist(json.get("data").index(0).get("attributes").get("name").text(), tracks, null, false);
    }

    public AudioItem getArtist(String id, String countryCode) throws IOException {
        var json = this.getJson(API_BASE + "catalog/" + countryCode + "/artists/" + id + "/view/top-songs");
        if (json == null || json.get("data").values().isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        return new BasicAudioPlaylist(json.get("data").index(0).get("attributes").get("artistName").text() + "'s Top Tracks", parseTracks(json), null, false);
    }

    public AudioItem getSong(String id, String countryCode) throws IOException {
        var json = this.getJson(API_BASE + "catalog/" + countryCode + "/songs/" + id);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }
        return parseTrack(json.get("data").index(0));
    }

    private List<AudioTrack> parseTracks(JsonBrowser json) {
        var tracks = new ArrayList<AudioTrack>();
        for (var value : json.get("data").values()) {
            tracks.add(this.parseTrack(value));
        }
        return tracks;
    }

    private AudioTrack parseTrack(JsonBrowser json) {
        var attributes = json.get("attributes");
        var artwork = attributes.get("artwork");
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
        try {
            this.httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close HTTP interface manager", e);
        }
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        this.httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        this.httpInterfaceManager.configureBuilder(configurator);
    }

}
