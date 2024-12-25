package com.github.topi314.lavasrc.tidal;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
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
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TidalSourceManager extends MirroringAudioSourceManager implements HttpConfigurable, AudioSearchManager {
    public static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:(?:listen|www)\\.)?tidal\\.com/(?:browse/)?(?<type>album|track|playlist|mix)/(?<id>[a-zA-Z0-9\\-]+)(?:\\?.*)?"
    );
    public static final String SEARCH_PREFIX = "tdsearch:";
    public static final String PUBLIC_API_BASE = "https://api.tidal.com/v1/";
    public static final int PLAYLIST_MAX_PAGE_ITEMS = 750;
    public static final int ALBUM_MAX_PAGE_ITEMS = 120;
    private static final String USER_AGENT = "TIDAL/3704 CFNetwork/1220.1 Darwin/20.3.0";
    private static final String TIDAL_TOKEN = "i4ZDjcyhed7Mu47q";
    private static final Logger log = LoggerFactory.getLogger(TidalSourceManager.class);
    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    private int searchLimit = 6;
    private final String countryCode;

    public TidalSourceManager(String[] providers, String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager) {
        this(countryCode, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
    }

    public TidalSourceManager(String countryCode, Function<Void, AudioPlayerManager> audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
        super(audioPlayerManager, mirroringAudioTrackResolver);
        this.countryCode = countryCode != null && !countryCode.isEmpty() ? countryCode : "US";
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    @NotNull
    @Override
    public String getSourceName() {
        return "tidal";
    }

    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        ExtendedAudioSourceManager.ExtendedAudioTrackInfo extendedAudioTrackInfo = super.decodeTrack(input);
        return new TidalAudioTrack(
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

    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            String identifier = reference.identifier;
            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (matcher.matches()) {
                String type = matcher.group("type");
                String id = matcher.group("id");
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
                String query = reference.identifier.substring(SEARCH_PREFIX.length());
                return (!query.isEmpty() ? this.getSearch(query) : AudioReference.NO_TRACK);
            }

        } catch (IOException var9) {
            throw new RuntimeException(var9);
        }
        return null;
    }

    private JsonBrowser getApiResponse(String apiUrl) throws IOException {
        HttpGet request = new HttpGet(apiUrl);
        request.setHeader("user-agent", USER_AGENT);
        request.setHeader("x-tidal-token", TIDAL_TOKEN);
        return LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
    }

    private List<AudioTrack> parseTracks(JsonBrowser json) {
        ArrayList<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser audio : json.values()) {
            AudioTrack parsedTrack = this.parseTrack(audio);
            if (parsedTrack != null) {
                tracks.add(parsedTrack);
            }
        }

        return tracks;
    }

    private AudioItem getSearch(String query) throws IOException {
        try {
            String apiUrl = "https://api.tidal.com/v1/search?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&offset=0&limit="
                    + this.searchLimit
                    + "&countryCode="
                    + this.countryCode;
            JsonBrowser json = this.getApiResponse(apiUrl);
            if (json.get("tracks").get("items").isNull()) {
                return AudioReference.NO_TRACK;
            } else {
                List<AudioTrack> tracks = this.parseTracks(json.get("tracks").get("items"));
                return (tracks.isEmpty() ? AudioReference.NO_TRACK : new BasicAudioPlaylist("Tidal Music Search: " + query,
	                tracks, null, true));
            }
        } catch (SocketTimeoutException var5) {
            return AudioReference.NO_TRACK;
        }
    }

    private AudioTrack parseTrack(JsonBrowser audio) {
        String id = audio.get("id").text();
        String rawDuration = audio.get("duration").text();
        if (rawDuration == null) {
            log.warn("Skipping track with null duration. Audio JSON: {}", audio);
            return null;
        } else {
            try {
                long duration = Long.parseLong(rawDuration) * 1000L;
                String title = audio.get("title").text();
                String originalUrl = audio.get("url").text();
                JsonBrowser artistsArray = audio.get("artists");
                StringBuilder artistName = new StringBuilder();

                for (int i = 0; i < artistsArray.values().size(); i++) {
                    String currentArtistName = artistsArray.index(i).get("name").text();
                    artistName.append(i > 0 ? ", " : "").append(currentArtistName);
                }

                String coverIdentifier = audio.get("album").get("cover").text();
                if (coverIdentifier == null) {
                    coverIdentifier = "https://tidal.com/_nuxt/img/logos.d8ce10b.jpg";
                }

                String isrc = audio.get("isrc").text();
                String formattedCoverIdentifier = coverIdentifier.replaceAll("-", "/");
                String artworkUrl = "https://resources.tidal.com/images/" + formattedCoverIdentifier + "/1280x1280.jpg";
                return new TidalAudioTrack(new AudioTrackInfo(title, artistName.toString(), duration, id, false, originalUrl, artworkUrl, isrc), this);
            } catch (NumberFormatException var14) {
                log.error("Error parsing duration for track. Audio JSON: {}", audio, var14);
                return null;
            }
        }
    }

    private AudioItem getAlbumOrPlaylist(String itemId, String type, int maxPageItems) throws IOException {
        try {
            String apiUrl = PUBLIC_API_BASE + type + "s/" + itemId + "/tracks?countryCode=" + this.countryCode + "&limit=" + maxPageItems;
            JsonBrowser json = this.getApiResponse(apiUrl);
            if (json != null && !json.get("items").isNull()) {
                List<AudioTrack> items = this.parseTrackItem(json);
                if (items.isEmpty()) {
                    return AudioReference.NO_TRACK;
                }

                String itemTitle = "";
                String itemInfoUrl = "";
                if (type.equalsIgnoreCase("playlist")) {
                    itemInfoUrl = "https://api.tidal.com/v1/playlists/" + itemId + "?countryCode=" + this.countryCode;
                } else if (type.equalsIgnoreCase("album")) {
                    itemInfoUrl = "https://api.tidal.com/v1/albums/" + itemId + "?countryCode=" + this.countryCode;
                }

                JsonBrowser itemInfoJson = this.getApiResponse(itemInfoUrl);
                if (itemInfoJson != null && !itemInfoJson.get("title").isNull()) {
                    itemTitle = itemInfoJson.get("title").text();
                }

                return new BasicAudioPlaylist(itemTitle, items, null, false);
            }

            return AudioReference.NO_TRACK;
        } catch (SocketTimeoutException var10) {
            log.error("Socket timeout while fetching {} info for ID: {}", new Object[]{type, itemId, var10});
        } catch (Exception var11) {
            log.error("Error fetching {} info for ID: {}", new Object[]{type, itemId, var11});
        }

        return AudioReference.NO_TRACK;
    }

    public AudioItem getTrack(String trackId) throws IOException {
        try {
            String apiUrl = "https://api.tidal.com/v1/tracks/" + trackId + "?countryCode=" + this.countryCode;
            JsonBrowser json = this.getApiResponse(apiUrl);
            if (json != null && !json.isNull()) {
                AudioTrack track = this.parseTrack(json);
                if (track == null) {
                    log.info("Failed to parse track for ID: {}", trackId);
                    return AudioReference.NO_TRACK;
                } else {
                    log.info("Track loaded successfully for ID: {}", trackId);
                    return track;
                }
            } else {
                log.info("Track not found for ID: {}", trackId);
                return AudioReference.NO_TRACK;
            }
        } catch (SocketTimeoutException var5) {
            log.error("Socket timeout while fetching track with ID: {}", trackId, var5);
            return AudioReference.NO_TRACK;
        }
    }

    public AudioItem getMix(String mixId) throws IOException {
        try {
            String apiUrl = "https://api.tidal.com/v1/mixes/" + mixId + "/items?countryCode=" + this.countryCode;
            JsonBrowser json = this.getApiResponse(apiUrl);
            if (json != null && !json.get("items").isNull()) {
                List<AudioTrack> items = this.parseTrackItem(json);
                if (items.isEmpty()) {
                    return AudioReference.NO_TRACK;
                } else {
                    log.info("Mix loaded successfully for ID: {}", mixId);
                    return new BasicAudioPlaylist("Mix: " + mixId, items, null, false);
                }
            } else {
                log.info("Mix not found for ID: {}", mixId);
                return AudioReference.NO_TRACK;
            }
        } catch (SocketTimeoutException var5) {
            log.error("Socket timeout while fetching track with ID: {}", mixId, var5);
            return AudioReference.NO_TRACK;
        }
    }

    private List<AudioTrack> parseTrackItem(JsonBrowser json) {
        ArrayList<AudioTrack> tracks = new ArrayList<>();
        JsonBrowser items = json.get("items");

        for (JsonBrowser audio : items.values()) {
            JsonBrowser item = audio.get("item").isNull() ? audio : audio.get("item");
            AudioTrack parsedTrack = this.parseTrack(item);
            if (parsedTrack != null) {
                tracks.add(parsedTrack);
            }
        }

        return tracks;
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
        } catch (IOException var2) {
            log.error("Failed to close HTTP interface manager", var2);
        }
    }

    @Override
    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }

    @Nullable
    @Override
    public AudioSearchResult loadSearch(@NotNull String query, @NotNull Set<AudioSearchResult.Type> types) {
        return null;
    }
}
