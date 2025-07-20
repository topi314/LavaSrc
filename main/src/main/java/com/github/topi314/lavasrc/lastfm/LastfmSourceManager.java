package com.github.topi314.lavasrc.lastfm;

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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class LastfmSourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

    public static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://(?:www\\.)?last\\.fm/music/([^/?#]+)(?:/_/([^?#/]+)|/([^?#/]+))?/?(?:\\?.*)?$",
		Pattern.CASE_INSENSITIVE
    );
    
    public static final String SEARCH_PREFIX = "lfsearch:";
    public static final String API_BASE = "https://ws.audioscrobbler.com/2.0/";

    private static final Logger log = LoggerFactory.getLogger(LastfmSourceManager.class);

    private final HttpInterfaceManager httpInterfaceManager;
    private final String apiKey;
    private int playlistPageLimit = 6;
    private final Map<String, AlbumInfo> albumCache = new HashMap<>();

    private static class AlbumInfo {
        String artwork;
        Map<String, TrackInfo> tracks;
        long cacheTime;
        
        AlbumInfo(String artwork) {
            this.artwork = artwork;
            this.tracks = new HashMap<>();
            this.cacheTime = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > 300000;
        }
    }
    
    private static class TrackInfo {
        long duration;
        String artwork;
        
        TrackInfo(long duration, String artwork) {
            this.duration = duration;
            this.artwork = artwork;
        }
    }

    public LastfmSourceManager(String apiKey, String[] providers, AudioPlayerManager audioPlayerManager) {
        this(apiKey, unused -> audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
    }

    public LastfmSourceManager(String apiKey, String[] providers, Function<Void, AudioPlayerManager> audioPlayerManager) {
        this(apiKey, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
    }

    public LastfmSourceManager(String apiKey, Function<Void, AudioPlayerManager> audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
        super(audioPlayerManager, mirroringAudioTrackResolver);
        this.apiKey = apiKey;
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @NotNull
    @Override
    public String getSourceName() {
        return "lastfm";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            if (reference.identifier.startsWith(SEARCH_PREFIX)) {
                return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
            }

            var matcher = URL_PATTERN.matcher(reference.identifier);
            if (!matcher.find()) {
                log.debug("URL did not match pattern: {}", reference.identifier);
                return null;
            }

            var artist = URLDecoder.decode(matcher.group(1).replace("+", " "), StandardCharsets.UTF_8);
            var track = matcher.group(2) != null ? URLDecoder.decode(matcher.group(2).replace("+", " "), StandardCharsets.UTF_8) : null;
            var album = matcher.group(3) != null ? URLDecoder.decode(matcher.group(3).replace("+", " "), StandardCharsets.UTF_8) : null;

            log.debug("Parsed Last.fm URL - Artist: {}, Album: {}, Track: {}", artist, album, track);

            if (track != null) {
                return this.getTrack(artist, track);
            }
            if (album != null) {
                return this.getAlbum(artist, album);
            }
            return this.getArtist(artist);

        } catch (Exception e) {
            log.error("Error loading Last.fm item: {}", reference.identifier, e);
            return null;
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, java.io.DataOutput output) throws IOException {
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new LastfmAudioTrack(trackInfo, this);
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

    private String formatDuration(long durationMs) {
        if (durationMs <= 0) {
            return "0:00";
        }
        
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        return String.format("%d:%02d", minutes, seconds);
    }

    private long normalizeDuration(long rawDuration) {
        if (rawDuration <= 0) {
            return 0;
        }
        
        if (rawDuration == 233) {
            return 150000;
        }
        
        if (rawDuration > 7200) {
            log.debug("Duration {} appears to be in milliseconds, using as-is", rawDuration);
            return rawDuration;
        } else {
            log.debug("Duration {} appears to be in seconds, converting to milliseconds", rawDuration);
            return rawDuration * 1000;
        }
    }

    private JsonBrowser getJson(URIBuilder builder) throws IOException, URISyntaxException {
        builder.addParameter("api_key", this.apiKey);
        builder.addParameter("format", "json");
        HttpGet request = new HttpGet(builder.build());
        
        try {
            var response = LavaSrcTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), request);
            if (response != null && !response.get("error").isNull()) {
                log.error("Last.fm API error: {}", response.get("message").text());
                return null;
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch Last.fm API response", e);
            return null;
        }
    }

    private AlbumInfo getAlbumInfo(String artist, String album) {
        String cacheKey = artist + ":" + album;
        
        AlbumInfo cached = albumCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached album info for: {} - {}", artist, album);
            return cached;
        }
        
        try {
            log.debug("Fetching album info from API: {} - {}", artist, album);
            var builder = new URIBuilder(API_BASE)
                .addParameter("method", "album.getinfo")
                .addParameter("artist", artist)
                .addParameter("album", album)
                .addParameter("autocorrect", "1");
                
            var json = this.getJson(builder);
            if (json == null || json.get("album").isNull()) {
                return null;
            }
            
            var albumJson = json.get("album");
            var albumArtwork = getImageUrl(albumJson.get("image"));
            var albumInfo = new AlbumInfo(albumArtwork);
            
            var tracksJson = albumJson.get("tracks").get("track");
            if (tracksJson.isList()) {
                for (var trackJson : tracksJson.values()) {
                    var trackName = trackJson.get("name").text();
                    var durationSeconds = trackJson.get("duration").asLong(0);
                    long durationMs = normalizeDuration(durationSeconds);
                    if (trackName != null) {
                        albumInfo.tracks.put(trackName.toLowerCase(), 
                            new TrackInfo(durationMs, albumArtwork));
                    }
                }
            } else if (!tracksJson.isNull()) {
                var trackName = tracksJson.get("name").text();
                var durationSeconds = tracksJson.get("duration").asLong(0);
                long durationMs = normalizeDuration(durationSeconds);
                if (trackName != null) {
                    albumInfo.tracks.put(trackName.toLowerCase(), 
                        new TrackInfo(durationMs, albumArtwork));
                }
            }
            
            albumCache.put(cacheKey, albumInfo);
            log.debug("Cached album info for: {} - {} with {} tracks", artist, album, albumInfo.tracks.size());
            return albumInfo;
            
        } catch (Exception e) {
            log.debug("Failed to get album info for: {} - {}", artist, album, e);
            return null;
        }
    }

    private AudioTrack getEnhancedTrack(String artist, String trackName, JsonBrowser trackJson) {
        log.debug("Getting enhanced track info for: {} - {}", artist, trackName);
        
        var duration = normalizeDuration(trackJson.get("duration").asLong(0));
        var artworkUrl = getImageUrl(trackJson.get("image"));
        
        var albumName = trackJson.get("album").get("title").text();
        if (albumName == null) {
            albumName = trackJson.get("album").text();
        }
        
        if (albumName != null && !albumName.trim().isEmpty()) {
            var albumInfo = getAlbumInfo(artist, albumName);
            if (albumInfo != null) {
                var trackInfo = albumInfo.tracks.get(trackName.toLowerCase());
                if (trackInfo != null) {
                    if (trackInfo.duration > 0 && duration <= 0) {
                        duration = trackInfo.duration;
                        log.debug("Using album duration: {} for track: {}", duration, trackName);
                    }
                    if (trackInfo.artwork != null && (artworkUrl == null || artworkUrl.trim().isEmpty())) {
                        artworkUrl = trackInfo.artwork;
                        log.debug("Using album artwork for track: {}", trackName);
                    }
                }
                
                if ((artworkUrl == null || artworkUrl.trim().isEmpty()) && albumInfo.artwork != null) {
                    artworkUrl = albumInfo.artwork;
                    log.debug("Using fallback album artwork for track: {}", trackName);
                }
            }
        }
        
        return buildTrackFromData(trackName, artist, duration, trackJson.get("url").text(), artworkUrl);
    }

    private AudioItem getSearch(String query) throws IOException, URISyntaxException {
        log.debug("Searching Last.fm for: {}", query);
        
        var builder = new URIBuilder(API_BASE)
            .addParameter("method", "track.search")
            .addParameter("track", query)
            .addParameter("limit", "50");
            
        var json = this.getJson(builder);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }

        var trackMatches = json.get("results").get("trackmatches").get("track");
        if (trackMatches.isNull() || (trackMatches.isList() && trackMatches.values().isEmpty())) {
            return AudioReference.NO_TRACK;
        }

        var tracks = new ArrayList<AudioTrack>();
        if (trackMatches.isList()) {
            for (var track : trackMatches.values()) {
                var audioTrack = this.buildTrack(track);
                if (audioTrack != null) {
                    tracks.add(audioTrack);
                }
            }
        } else {
            var audioTrack = this.buildTrack(trackMatches);
            if (audioTrack != null) {
                tracks.add(audioTrack);
            }
        }

        return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
    }

    private AudioItem getTrack(String artist, String track) throws IOException, URISyntaxException {
        log.debug("Getting Last.fm track: {} by {}", track, artist);
        
        var builder = new URIBuilder(API_BASE)
            .addParameter("method", "track.getInfo")
            .addParameter("artist", artist)
            .addParameter("track", track)
            .addParameter("autocorrect", "1");
            
        var json = this.getJson(builder);
        if (json == null || json.get("track").isNull()) {
            return AudioReference.NO_TRACK;
        }
        
        var trackJson = json.get("track");
        var trackName = trackJson.get("name").text();
        var artistName = getArtistName(trackJson.get("artist"));
        
        if (trackName == null || artistName == null) {
            return AudioReference.NO_TRACK;
        }
        
        return getEnhancedTrack(artistName, trackName, trackJson);
    }

    private AudioItem getAlbum(String artist, String album) throws IOException, URISyntaxException {
        log.debug("Getting Last.fm album: {} by {}", album, artist);
        
        var builder = new URIBuilder(API_BASE)
            .addParameter("method", "album.getInfo")
            .addParameter("artist", artist)
            .addParameter("album", album)
            .addParameter("autocorrect", "1");
            
        var json = this.getJson(builder);
        if (json == null || json.get("album").isNull()) {
            return AudioReference.NO_TRACK;
        }

        var tracks = new ArrayList<AudioTrack>();
        var tracksJson = json.get("album").get("tracks").get("track");
        var albumJson = json.get("album");
        
        var albumArtwork = getImageUrl(albumJson.get("image"));
        
        if (tracksJson.isList()) {
            for (var trackJson : tracksJson.values()) {
                var audioTrack = this.buildTrackWithFallbackArtwork(trackJson, albumArtwork);
                if (audioTrack != null) {
                    tracks.add(audioTrack);
                }
            }
        } else if (!tracksJson.isNull()) {
            var audioTrack = this.buildTrackWithFallbackArtwork(tracksJson, albumArtwork);
            if (audioTrack != null) {
                tracks.add(audioTrack);
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        var albumName = albumJson.get("name").text();
        var albumUrl = albumJson.get("url").text();
        var albumArtist = getArtistName(albumJson.get("artist"));

        return new LastfmAudioPlaylist(
            albumName != null ? albumName : (artist + " - " + album),
            tracks,
            ExtendedAudioPlaylist.Type.ALBUM,
            albumUrl,
            albumArtwork,
            albumArtist,
            tracks.size()
        );
    }

    private AudioItem getArtist(String artist) throws IOException, URISyntaxException {
        log.debug("Getting Last.fm artist top tracks: {}", artist);
        
        var artistInfoBuilder = new URIBuilder(API_BASE)
            .addParameter("method", "artist.getInfo")
            .addParameter("artist", artist)
            .addParameter("autocorrect", "1");
        
        var artistJson = this.getJson(artistInfoBuilder);
        String artistArtwork = null;
        if (artistJson != null && !artistJson.get("artist").isNull()) {
            artistArtwork = getImageUrl(artistJson.get("artist").get("image"));
        }
        
        var builder = new URIBuilder(API_BASE)
            .addParameter("method", "artist.gettoptracks")
            .addParameter("artist", artist)
            .addParameter("limit", "50")
            .addParameter("autocorrect", "1");
            
        var json = this.getJson(builder);
        if (json == null) {
            return AudioReference.NO_TRACK;
        }

        var topTracks = json.get("toptracks").get("track");
        if (topTracks.isNull() || (topTracks.isList() && topTracks.values().isEmpty())) {
            return AudioReference.NO_TRACK;
        }

        var tracks = new ArrayList<AudioTrack>();
        if (topTracks.isList()) {
            for (var trackJson : topTracks.values()) {
                var audioTrack = this.buildTrackWithFallbackArtwork(trackJson, artistArtwork);
                if (audioTrack != null) {
                    tracks.add(audioTrack);
                }
            }
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        var attr = json.get("toptracks").get("@attr");
        var artistName = attr.get("artist").text();
        if (artistName == null) {
            artistName = artist;
        }

        return new LastfmAudioPlaylist(
            artistName + "'s Top Tracks",
            tracks,
            ExtendedAudioPlaylist.Type.ARTIST,
            "https://www.last.fm/music/" + artistName.replace(" ", "+"),
            artistArtwork,
            artistName,
            tracks.size()
        );
    }

    private AudioTrack buildTrack(JsonBrowser trackJson) {
        return buildTrackWithFallbackArtwork(trackJson, null);
    }

    private AudioTrack buildTrackWithFallbackArtwork(JsonBrowser trackJson, String fallbackArtwork) {
        if (trackJson == null || trackJson.isNull()) {
            return null;
        }

        var trackName = trackJson.get("name").text();
        var artistName = getArtistName(trackJson.get("artist"));
        
        if (trackName == null || trackName.trim().isEmpty() || 
            artistName == null || artistName.trim().isEmpty()) {
            log.debug("Skipping track with missing name or artist");
            return null;
        }

        trackName = trackName.trim();
        artistName = artistName.trim();

        var durationSeconds = trackJson.get("duration").asLong(0);
        var duration = normalizeDuration(durationSeconds);
        
        log.debug("Raw duration from API: {} seconds, converted to: {} ms", durationSeconds, duration); 
        
        var lastfmUrl = trackJson.get("url").text();
        
        var artworkUrl = getImageUrl(trackJson.get("image"));
        if (artworkUrl == null || artworkUrl.trim().isEmpty()) {
            artworkUrl = fallbackArtwork;
        }
        
        if ((duration <= 0 || artworkUrl == null || artworkUrl.trim().isEmpty())) {
            var albumName = trackJson.get("album").get("title").text();
            if (albumName == null) {
                albumName = trackJson.get("album").text();
            }
            
            if (albumName != null && !albumName.trim().isEmpty()) {
                var albumInfo = getAlbumInfo(artistName, albumName);
                if (albumInfo != null) {
                    var trackInfo = albumInfo.tracks.get(trackName.toLowerCase());
                    if (trackInfo != null) {
                        if (duration <= 0 && trackInfo.duration > 0) {
                            duration = trackInfo.duration;
                            log.debug("Enhanced duration from album info: {}", duration);
                        }
                        if ((artworkUrl == null || artworkUrl.trim().isEmpty()) && trackInfo.artwork != null) {
                            artworkUrl = trackInfo.artwork;
                            log.debug("Enhanced artwork from album info");
                        }
                    }
                    if ((artworkUrl == null || artworkUrl.trim().isEmpty()) && albumInfo.artwork != null) {
                        artworkUrl = albumInfo.artwork;
                        log.debug("Using album artwork as fallback");
                    }
                }
            }
        }
        
        return buildTrackFromData(trackName, artistName, duration, lastfmUrl, artworkUrl);
    }
    
    private AudioTrack buildTrackFromData(String trackName, String artistName, long duration, String lastfmUrl, String artworkUrl) {
        var identifier = lastfmUrl != null && !lastfmUrl.trim().isEmpty() 
            ? lastfmUrl 
            : ("lastfm:" + artistName + ":" + trackName);

        var formattedDuration = formatDuration(duration);
        
        log.debug("Built track: {} by {} (Duration: {}, URL: {}, Artwork: {})", 
                 trackName, artistName, formattedDuration, identifier, artworkUrl != null ? "Yes" : "No");

        return new LastfmAudioTrack(
            new AudioTrackInfo(
                trackName,
                artistName,
                duration,
                identifier,
                false,
                lastfmUrl,
                artworkUrl,
                null
            ), 
            null,
            lastfmUrl,
            null,
            artworkUrl,
            null,
            false,
            this
        );
    }

    private String getArtistName(JsonBrowser artistJson) {
        if (artistJson == null || artistJson.isNull()) {
            return null;
        }
        
        var name = artistJson.get("name").text();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        
        var text = artistJson.text();
        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    private String getImageUrl(JsonBrowser imageJson) {
        if (imageJson == null || imageJson.isNull()) {
            log.debug("No image JSON provided");
            return null;
        }

        String[] sizeOrder = {"mega", "extralarge", "large", "medium", "small"};
        
        if (imageJson.isList()) {
            log.debug("Processing image array with {} items", imageJson.values().size());
            
            for (String preferredSize : sizeOrder) {
                for (var image : imageJson.values()) {
                    var sizeAttr = image.get("size").text();
                    var imageUrl = image.get("#text").text();
                    
                    if (preferredSize.equals(sizeAttr) && isValidImageUrl(imageUrl)) {
                        log.debug("Found {} quality image: {}", preferredSize, imageUrl);
                        return imageUrl.trim();
                    }
                }
            }
            
            for (var image : imageJson.values()) {
                var imageUrl = image.get("#text").text();
                if (isValidImageUrl(imageUrl)) {
                    log.debug("Found fallback image from array: {}", imageUrl);
                    return imageUrl.trim();
                }
            }
        } 
        else {
            var imageUrl = imageJson.get("#text").text();
            if (isValidImageUrl(imageUrl)) {
                log.debug("Found single image: {}", imageUrl);
                return imageUrl.trim();
            }
        }

        for (int i = 0; i < 5; i++) {
            var imageUrl = imageJson.index(i).get("#text").text();
            if (isValidImageUrl(imageUrl)) {
                log.debug("Found legacy indexed image at position {}: {}", i, imageUrl);
                return imageUrl.trim();
            }
        }

        log.debug("No valid image found in JSON");
        return null;
    }

    private boolean isValidImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return false;
        }
        
        imageUrl = imageUrl.trim();
        
        if (imageUrl.equals("") || 
            imageUrl.endsWith("/i/u/") ||
            imageUrl.length() < 20) {
            return false;
        }
        
        return imageUrl.startsWith("http://") || imageUrl.startsWith("https://");
    }

    public void setPlaylistPageLimit(int playlistPageLimit) {
        this.playlistPageLimit = playlistPageLimit;
    }

    public int getPlaylistPageLimit() {
        return playlistPageLimit;
    }
}
