package com.github.topi314.lavasrc.amazonmusic;

import com.github.topi314.lavasrc.amazonmusic.AmazonMusicParser;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;

public class AmazonMusicSourceManager implements AudioSourceManager {
    private static final String AMAZON_MUSIC_URL_REGEX =
        "https?:\\/\\/music\\.amazon\\.[a-z\\.]+\\/(tracks|albums|playlists|artists|podcast|episode|lyrics)\\/([A-Za-z0-9]+)";
    private static final Pattern AMAZON_MUSIC_URL_PATTERN = Pattern.compile(AMAZON_MUSIC_URL_REGEX);

    private final String apiUrl;
    private final String apiKey;

    public AmazonMusicSourceManager(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    public AmazonMusicSourceManager(String apiUrl) {
        this(apiUrl, null);
    }

    @Override
    public String getSourceName() {
        return "amazonmusic";
    }

    public AudioItem loadItem(com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager manager, AudioReference reference) {
        // Remove additional parameters from the URL, leaving only the base link
        String cleanedIdentifier = reference.identifier.split("\\?")[0];
        Matcher matcher = AMAZON_MUSIC_URL_PATTERN.matcher(cleanedIdentifier);
        if (!matcher.matches()) {
            return null;
        }

        String type = matcher.group(1);
        String id = matcher.group(2);

        id = id.split("[^a-zA-Z0-9]", 2)[0];

        // Added declaration of the trackAsin variable
        String trackAsin = reference.identifier.contains("?") ? extractQueryParam(reference.identifier, "trackAsin") : null;

        try {
            // Handle podcasts
            if ("podcast".equals(type)) {
                AlbumJson podcastJson = fetchPodcastInfo(id);
                if (podcastJson == null || podcastJson.tracks == null || podcastJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new ArrayList<>();
                for (TrackJson track : podcastJson.tracks) {
                    AudioUrlResult audioResult = track.audioUrl != null
                        ? new AudioUrlResult(track.audioUrl, track.artworkUrl, track.isrc)
                        : fetchAudioUrlFromStreamUrls(track.id);
                    if (audioResult == null || audioResult.audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for podcast track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioResult.audioUrl);
                    tracks.add(new AmazonMusicAudioTrack(info, audioResult.audioUrl, audioResult.isrc, audioResult.artworkUrl, this));
                }
                return new BasicAudioPlaylist(podcastJson.title != null ? podcastJson.title : "Podcast", tracks, null, false);
            }

            // Handle episodes
            if ("episode".equals(type)) {
                TrackJson episodeJson = fetchEpisodeInfo(id);
                if (episodeJson == null) return null;
                AudioUrlResult audioResult = episodeJson.audioUrl != null
                    ? new AudioUrlResult(episodeJson.audioUrl, episodeJson.artworkUrl, episodeJson.isrc)
                    : fetchAudioUrlFromStreamUrls(episodeJson.id);
                if (audioResult == null || audioResult.audioUrl == null) {
                    System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for episode: " + episodeJson.id);
                    return null;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    episodeJson.title,
                    episodeJson.artist,
                    episodeJson.duration,
                    episodeJson.id != null ? episodeJson.id : "",
                    false,
                    reference.identifier
                );
                System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioResult.audioUrl);
                return new AmazonMusicAudioTrack(info, audioResult.audioUrl, audioResult.isrc, audioResult.artworkUrl, this);
            }

            // Handle song lyrics
            if ("lyrics".equals(type)) {
                String lyrics = fetchLyrics(id);
                if (lyrics == null) {
                    System.err.println("[AmazonMusic] [ERROR] No lyrics found for track: " + id);
                    return null;
                }
                System.out.println("Lyrics for track " + id + ":");
                System.out.println(lyrics);
                return null; // Lyrics are not playable
            }

            // Handle account information
            if ("account".equals(type)) {
                String accountInfo = fetchAccountInfo();
                if (accountInfo == null) {
                    System.err.println("[AmazonMusic] [ERROR] Failed to fetch account information.");
                    return null;
                }
                System.out.println("Account Information:");
                System.out.println(accountInfo);
                return null; // Account information is not playable
            }

            // Handle album with trackAsin (single track from album)
            if ("albums".equals(type) && trackAsin != null) {
                AlbumJson albumJson = fetchAlbumInfo(id);
                if (albumJson == null || albumJson.tracks == null || albumJson.tracks.length == 0) return null;
                TrackJson foundTrack = null;
                for (TrackJson track : albumJson.tracks) {
                    if (trackAsin.equals(track.id) || trackAsin.equals(extractJsonString(trackToJson(track), "asin", null))) {
                        foundTrack = track;
                        break;
                    }
                }
                if (foundTrack == null) {
                    System.err.println("[AmazonMusic] [ERROR] No track with id/asIn=" + trackAsin + " found in album " + id);
                    return null;
                }
                if (foundTrack.audioUrl == null) {
                    AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(foundTrack.id != null ? foundTrack.id : trackAsin);
                    if (audioResult != null) {
                        foundTrack.audioUrl = audioResult.audioUrl;
                        foundTrack.artworkUrl = audioResult.artworkUrl;
                        foundTrack.isrc = audioResult.isrc;
                    }
                }
                if (foundTrack.audioUrl == null) {
                    System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is still null after stream_urls for track: " + foundTrack.id);
                    return null;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    foundTrack.title,
                    foundTrack.artist,
                    foundTrack.duration,
                    foundTrack.id != null ? foundTrack.id : "",
                    false,
                    reference.identifier
                );
                System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + foundTrack.audioUrl);
                return new AmazonMusicAudioTrack(info, foundTrack.audioUrl, foundTrack.isrc, foundTrack.artworkUrl, this);
            } else if ("tracks".equals(type)) {
                String trackId = id;
                TrackJson trackJson = fetchTrackInfo(trackId);
                if (trackJson == null) return null;
                if (trackJson.audioUrl == null) {
                    AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(trackJson.id != null ? trackJson.id : trackId);
                    if (audioResult != null) {
                        trackJson.audioUrl = audioResult.audioUrl;
                        trackJson.artworkUrl = audioResult.artworkUrl;
                        trackJson.isrc = audioResult.isrc;
                    }
                }
                if (trackJson.audioUrl == null) {
                    System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for track: " + trackJson.id);
                    return null;
                }
                AudioTrackInfo info = new AudioTrackInfo(
                    trackJson.title,
                    trackJson.artist,
                    trackJson.duration,
                    trackJson.id != null ? trackJson.id : "",
                    false,
                    reference.identifier
                );
                System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + trackJson.audioUrl);
                AmazonMusicAudioTrack track = new AmazonMusicAudioTrack(info, trackJson.audioUrl, trackJson.isrc, trackJson.artworkUrl, this);
                return track;
            } else if ("albums".equals(type)) {
                AlbumJson albumJson = fetchAlbumInfo(id);
                if (albumJson == null || albumJson.tracks == null || albumJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new java.util.ArrayList<>();
                for (TrackJson track : albumJson.tracks) {
                    String audioUrl = track.audioUrl;
                    String artworkUrl = track.artworkUrl;
                    String isrc = track.isrc;
                    if (audioUrl == null) {
                        AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(track.id);
                        if (audioResult != null) {
                            audioUrl = audioResult.audioUrl;
                            artworkUrl = audioResult.artworkUrl;
                            isrc = audioResult.isrc;
                        }
                    }
                    if (audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for album track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioUrl);
                    AmazonMusicAudioTrack audioTrack = new AmazonMusicAudioTrack(info, audioUrl, isrc, artworkUrl, this);
                    tracks.add(audioTrack);
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(albumJson.title != null ? albumJson.title : "Amazon Music Album", tracks, null, false);
            } else if ("playlists".equals(type)) {
                PlaylistJson playlistJson = fetchPlaylistInfo(id);
                if (playlistJson == null || playlistJson.tracks == null || playlistJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new java.util.ArrayList<>();
                for (TrackJson track : playlistJson.tracks) {
                    String audioUrl = track.audioUrl;
                    String artworkUrl = track.artworkUrl;
                    String isrc = track.isrc;
                    if (audioUrl == null) {
                        AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(track.id);
                        if (audioResult != null) {
                            audioUrl = audioResult.audioUrl;
                            artworkUrl = audioResult.artworkUrl;
                            isrc = audioResult.isrc;
                        }
                    }
                    if (audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for playlist track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioUrl);
                    tracks.add(new AmazonMusicAudioTrack(info, audioUrl, isrc, artworkUrl, this));
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(playlistJson.title != null ? playlistJson.title : "Amazon Music Playlist", tracks, null, false);
            } else if ("artists".equals(type)) {
                ArtistJson artistJson = fetchArtistInfo(id);
                if (artistJson == null || artistJson.tracks == null || artistJson.tracks.length == 0) return null;
                List<AudioTrack> tracks = new ArrayList<>();
                for (TrackJson track : artistJson.tracks) {
                    String audioUrl = track.audioUrl;
                    String artworkUrl = track.artworkUrl;
                    String isrc = track.isrc;
                    if (audioUrl == null) {
                        AudioUrlResult audioResult = fetchAudioUrlFromStreamUrls(track.id);
                        if (audioResult != null) {
                            audioUrl = audioResult.audioUrl;
                            artworkUrl = audioResult.artworkUrl;
                            isrc = audioResult.isrc;
                        }
                    }
                    if (audioUrl == null) {
                        System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null for artist track: " + track.id);
                        continue;
                    }
                    AudioTrackInfo info = new AudioTrackInfo(
                        track.title,
                        track.artist,
                        track.duration,
                        track.id != null ? track.id : "",
                        false,
                        reference.identifier
                    );
                    System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + audioUrl);
                    tracks.add(new AmazonMusicAudioTrack(info, audioUrl, isrc, artworkUrl, this));
                }
                if (tracks.isEmpty()) return null;
                return new BasicAudioPlaylist(artistJson.name != null ? artistJson.name : "Amazon Music Artist", tracks, null, false);
            } else {
                System.err.println("[AmazonMusic] [ERROR] Unsupported type: " + type);
                return null;
            }
        } catch (IOException e) {
            System.err.println("[AmazonMusic] [ERROR] Network error while loading item: " + e.getMessage());
            e.printStackTrace();
            throw new FriendlyException("Failed to load Amazon Music item due to network error", FriendlyException.Severity.FAULT, e);
        } catch (Exception e) {
            System.err.println("[AmazonMusic] [ERROR] Unexpected error while loading item: " + e.getMessage());
            e.printStackTrace();
            throw new FriendlyException("Failed to load Amazon Music item due to unexpected error", FriendlyException.Severity.FAULT, e);
        }
    }

    /**
     * Searches for tracks on Amazon Music.
     *
     * @param query The search query.
     * @param limit The maximum number of results to return.
     * @return List of found AudioTrack objects (never null).
     */
    public java.util.List<AudioTrack> search(String query, int limit) {
        try {
            return searchTracks(query, limit);
        } catch (Exception e) {
            throw new FriendlyException("Failed to search Amazon Music: " + e.getMessage(), FriendlyException.Severity.COMMON, e);
        }
    }

    private List<AudioTrack> searchTracks(String query, int limit) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "search?query=" + encode(query) : apiUrl + "/search?query=" + encode(query);
        if (limit > 0) url += "&limit=" + limit;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return new ArrayList<>();
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();

        String json = content.toString();
        // Parse JSON array of tracks (minimal parser)
        List<AudioTrack> tracks = new ArrayList<>();
        int idx = json.indexOf("[");
        int end = json.indexOf("]", idx);
        if (idx == -1 || end == -1) return tracks;
        String arr = json.substring(idx + 1, end);
        String[] items = arr.split("\\},\\{");
        for (String item : items) {
            String obj = item;
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";
            TrackJson t = new TrackJson();
            t.title = extractJsonString(obj, "title", "Unknown Title");
            // Try to extract artist from object or string (object first)
            t.artist = extractArtistFlexible(obj, "Unknown Artist");
            t.duration = extractJsonLong(obj, "duration", 0L);
            t.audioUrl = extractJsonString(obj, "audioUrl", null);
            t.id = extractJsonString(obj, "id", null);
            // Add parsing for asin, image, isrc
            t.asin = extractJsonString(obj, "asin", null);
            t.artworkUrl = extractJsonString(obj, "image", null);
            t.isrc = extractJsonString(obj, "isrc", null);
            if (t.audioUrl == null || !isSupportedAudioFormat(t.audioUrl)) {
                System.err.println("[AmazonMusicSourceManager] [ERROR] audioUrl is null or unsupported for search track: " + t.id);
                continue;
            }
            AudioTrackInfo info = new AudioTrackInfo(
                t.title,
                t.artist,
                t.duration,
                t.id != null ? t.id : "",
                false,
                t.audioUrl
            );
            System.out.println("[AmazonMusicSourceManager] [DEBUG] Creating AmazonMusicAudioTrack: " + info + ", audioUrl=" + t.audioUrl);
            tracks.add(new AmazonMusicAudioTrack(info, t.audioUrl, t.isrc, t.artworkUrl, this));
        }
        return tracks;
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return s;
        }
    }

    private static class AlbumJson {
        String title;
        TrackJson[] tracks;
    }
    private static class PlaylistJson {
        String title;
        TrackJson[] tracks;
    }
    private static class ArtistJson {
        String name;
        TrackJson[] tracks;
    }

    private AlbumJson fetchAlbumInfo(String albumId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "album?id=" + albumId : apiUrl + "/album?id=" + albumId;
        return fetchTracksContainer(url, AlbumJson.class);
    }
    private PlaylistJson fetchPlaylistInfo(String playlistId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "playlist?id=" + playlistId : apiUrl + "/playlist?id=" + playlistId;
        return fetchTracksContainer(url, PlaylistJson.class);
    }
    private ArtistJson fetchArtistInfo(String artistId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "artist?id=" + artistId : apiUrl + "/artist?id=" + artistId;
        return fetchTracksContainer(url, ArtistJson.class);
    }

    /**
     * Fetches community playlist information.
     */
    private PlaylistJson fetchCommunityPlaylistInfo(String playlistId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "community_playlist?id=" + playlistId : apiUrl + "/community_playlist?id=" + playlistId;
        return fetchTracksContainer(url, PlaylistJson.class);
    }

    /**
     * Fetches episode information.
     */
    private TrackJson fetchEpisodeInfo(String episodeId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "episode?id=" + episodeId : apiUrl + "/episode?id=" + episodeId;
        return fetchTracksContainer(url, TrackJson.class);
    }

    /**
     * Fetches podcast information.
     */
    private AlbumJson fetchPodcastInfo(String podcastId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "podcast?id=" + podcastId : apiUrl + "/podcast?id=" + podcastId;
        return fetchTracksContainer(url, AlbumJson.class);
    }

    /**
     * Fetches lyrics for a track.
     */
    private String fetchLyrics(String trackId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "lyrics?id=" + trackId : apiUrl + "/lyrics?id=" + trackId;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return null;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();
        return content.toString();
    }

    /**
     * Fetches account information.
     */
    private String fetchAccountInfo() throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "account" : apiUrl + "/account";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return null;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();
        return content.toString();
    }

    private <T> T fetchTracksContainer(String url, Class<T> clazz) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return null;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();

        String json = content.toString();

        if (clazz == AlbumJson.class) {
            AlbumJson result = new AlbumJson();
            result.title = extractJsonString(json, "title", "Amazon Music Album");
            result.tracks = extractTracksArray(json);
            return clazz.cast(result);
        } else if (clazz == PlaylistJson.class) {
            PlaylistJson result = new PlaylistJson();
            result.title = extractJsonString(json, "title", "Amazon Music Playlist");
            result.tracks = extractTracksArray(json);
            return clazz.cast(result);
        } else if (clazz == ArtistJson.class) {
            ArtistJson result = new ArtistJson();
            result.name = extractJsonString(json, "name", "Amazon Music Artist");
            result.tracks = extractTracksArray(json);
            return clazz.cast(result);
        }
        return null;
    }

    private TrackJson[] extractTracksArray(String json) {
        int idx = json.indexOf("\"tracks\"");
        if (idx == -1) return new TrackJson[0];
        int start = json.indexOf('[', idx);
        int end = json.indexOf(']', start);
        if (start == -1 || end == -1) return new TrackJson[0];
        String arr = json.substring(start + 1, end);
        String[] items = arr.split("\\},\\{");
        java.util.List<TrackJson> tracks = new java.util.ArrayList<>();
        for (String item : items) {
            String obj = item;
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";
            TrackJson t = new TrackJson();
            t.title = extractJsonString(obj, "title", "Unknown Title");
            t.artist = extractArtistFlexible(obj, "Unknown Artist");
            t.duration = extractJsonLong(obj, "duration", 0L);
            t.audioUrl = extractJsonString(obj, "audioUrl", null);
            t.id = extractJsonString(obj, "id", null);
            t.asin = extractJsonString(obj, "asin", null);
            t.artworkUrl = extractJsonString(obj, "image", null);
            t.isrc = extractJsonString(obj, "isrc", null);
            tracks.add(t);
        }
        return tracks.toArray(new TrackJson[0]);
    }

    private TrackJson fetchTrackInfo(String trackId) throws IOException {
        String url = apiUrl.endsWith("/") ? apiUrl + "track?id=" + trackId : apiUrl + "/track?id=" + trackId;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        int status = conn.getResponseCode();
        if (status != 200) {
            return null;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            content.append(line);
        }
        in.close();
        conn.disconnect();

        String json = content.toString();
        TrackJson result = new TrackJson();
        result.title = extractJsonString(json, "title", "Unknown Title");
        result.artist = extractArtistFlexible(json, "Unknown Artist");
        result.duration = extractJsonLong(json, "duration", 0L);
        result.audioUrl = extractJsonString(json, "audioUrl", null);
        result.id = extractJsonString(json, "id", null);
        result.asin = extractJsonString(json, "asin", null);
        result.artworkUrl = extractJsonString(json, "image", null);
        result.isrc = extractJsonString(json, "isrc", null);
        return result;
    }

    // Extract artist from object ("artist":{"name":"..."}) or string ("artist":"..."), prefer object
    private static String extractArtistFlexible(String json, String def) {
        java.util.regex.Matcher objMatcher = java.util.regex.Pattern.compile("\"artist\"\\s*:\\s*\\{[^}]*\"name\"\\s*:\\s*\"(.*?)\"").matcher(json);
        if (objMatcher.find()) return objMatcher.group(1);
        java.util.regex.Matcher strMatcher = java.util.regex.Pattern.compile("\"artist\"\\s*:\\s*\"(.*?)\"").matcher(json);
        return strMatcher.find() ? strMatcher.group(1) : def;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        if (track instanceof AmazonMusicAudioTrack) {
            ((AmazonMusicAudioTrack) track).encode(output);
        } else {
            throw new IllegalArgumentException("Unsupported track type for encoding.");
        }
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return AmazonMusicAudioTrack.decode(trackInfo, input, this);
    }

    @Override
    public void shutdown() {
    }

	/**
	 * Fetches audioUrl, artworkUrl i isrc z /stream_urls?id={track_id}.
	 */
	private AudioUrlResult fetchAudioUrlFromStreamUrls(String trackId) throws IOException {
		if (trackId == null) {
			System.err.println("[AmazonMusic] [ERROR] Track ID is null.");
			return null;
		}

		String url = apiUrl.endsWith("/") ? apiUrl + "stream_urls?id=" + trackId : apiUrl + "/stream_urls?id=" + trackId;
		System.out.println("[AmazonMusic] [DEBUG] Fetching stream URLs from: " + url);

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		if (apiKey != null && !apiKey.isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + apiKey);
		}

		int status = conn.getResponseCode();
		InputStream inputStream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

		BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
		StringBuilder content = new StringBuilder();
		String line;
		while ((line = in.readLine()) != null) {
			content.append(line);
		}
		in.close();
		conn.disconnect();

		String json = content.toString();
		System.out.println("[AmazonMusic] [DEBUG] Response JSON: " + json);

		if (status != 200) {
			System.err.println("[AmazonMusic] [ERROR] Failed to fetch stream_urls for track: " + trackId);
			System.err.println("Response: " + json);
			return null;
		}

		// 1. Szukaj klasycznego audioUrl
		String audioUrl = null;
		String artworkUrl = null;
		String isrc = null;
		java.util.regex.Matcher urlsMatcher = java.util.regex.Pattern.compile("\"urls\"\\s*:\\s*\\{(.*?)\\}").matcher(json);
		if (urlsMatcher.find()) {
			String urlsContent = urlsMatcher.group(1);
			audioUrl = extractJsonString(urlsContent, "high");
			if (audioUrl == null) audioUrl = extractJsonString(urlsContent, "medium");
			if (audioUrl == null) audioUrl = extractJsonString(urlsContent, "low");
		}
		if (audioUrl == null) {
			audioUrl = extractJsonString(json, "audioUrl");
		}
		// 2. Szukaj bezpośredniego linku do pliku mp3/flac/opus oraz artworkUrl i isrc z data[]
		if ((audioUrl == null || !isSupportedAudioFormat(audioUrl))) {
			String bestUrl = null;
			String bestArtwork = null;
			String bestIsrc = null;
			String bestMime = null;
			int bestQuality = -1;
			java.util.regex.Matcher dataArrayMatcher = java.util.regex.Pattern.compile("\"data\"\\s*:\\s*\\[(.*?)\\](?!\\s*,)").matcher(json);
			if (dataArrayMatcher.find()) {
				String dataArray = dataArrayMatcher.group(1);
				java.util.regex.Matcher entryMatcher = java.util.regex.Pattern.compile("\\{(.*?)\\}(,|$)").matcher(dataArray);
				while (entryMatcher.find()) {
					String entry = entryMatcher.group(1);

					String mimeType = extractJsonString(entry, "mime_type");
					String baseUrl = extractJsonString(entry, "base_url");
					String codecs = extractJsonString(entry, "codecs");
					String entryArtwork = extractJsonString(entry, "image");
					String entryIsrc = extractJsonString(entry, "isrc");
					int qualityRanking = -1;
					try {
						java.util.regex.Matcher qMatcher = java.util.regex.Pattern.compile("\"quality_ranking\"\\s*:\\s*(\\d+)").matcher(entry);
						if (qMatcher.find()) qualityRanking = Integer.parseInt(qMatcher.group(1));
					} catch (Exception ignore) {}

					// Preferencje: mp3 > opus > flac > audio/mp4
					int score = 0;
					if (mimeType != null && mimeType.contains("mp3")) score = 100;
					else if (codecs != null && codecs.contains("opus")) score = 90;
					else if (codecs != null && codecs.contains("flac")) score = 80;
					else if (mimeType != null && mimeType.contains("audio/mp4")) score = 70;
					else score = 10;

					// Im wyższy qualityRanking, tym lepiej (jeśli score równe)
					if (baseUrl != null && score > bestQuality) {
						bestUrl = baseUrl;
						bestArtwork = entryArtwork;
						bestIsrc = entryIsrc;
						bestMime = mimeType;
						bestQuality = score;
					} else if (baseUrl != null && score == bestQuality && qualityRanking > 0) {
						bestUrl = baseUrl;
						bestArtwork = entryArtwork;
						bestIsrc = entryIsrc;
						bestMime = mimeType;
						bestQuality = score;
					}
				}
			}
			if (bestUrl != null) {
				System.out.println("[AmazonMusic] [DEBUG] Final base_url: " + bestUrl + " (mime_type: " + bestMime + ")");
				audioUrl = bestUrl;
				artworkUrl = bestArtwork;
				isrc = bestIsrc;
			}
		}

		// Log error jeśli nadal nie znaleziono
		if (audioUrl == null) {
			System.err.println("[AmazonMusic] [ERROR] audioUrl is still null after processing stream_urls for track: " + trackId);
			return null;
		}

		return new AudioUrlResult(audioUrl, artworkUrl, isrc);
	}

	/**
	 * Extracts a JSON string value for a given key using regex (no JSON parser used).
	 */
	private String extractJsonString(String json, String key) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"").matcher(json);
		return matcher.find() ? matcher.group(1) : null;
	}

    private String extractJsonString(String json, String key, String def) {
        String value = extractJsonString(json, key);
        return value != null ? value : def;
    }

    private long extractJsonLong(String json, String key, long def) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : def;
    }

    // Helper to convert TrackJson to JSON string for parser (full data)
    private String trackToJson(TrackJson track) {
        StringBuilder sb = new StringBuilder("{");
        if (track.id != null) sb.append("\"id\":\"").append(track.id).append("\",");
        if (track.title != null) sb.append("\"title\":\"").append(track.title).append("\",");
        if (track.artist != null) sb.append("\"artist\":\"").append(track.artist).append("\",");
        sb.append("\"duration\":").append(track.duration).append(",");
        if (track.audioUrl != null) sb.append("\"audioUrl\":\"").append(track.audioUrl).append("\",");
        if (track.asin != null) sb.append("\"asin\":\"").append(track.asin).append("\",");
        if (track.artworkUrl != null) sb.append("\"image\":\"").append(track.artworkUrl).append("\",");
        if (track.isrc != null) sb.append("\"isrc\":\"").append(track.isrc).append("\"");
        sb.append("}");
        return sb.toString().replace(",}", "}");
    }

    private static boolean isSupportedAudioFormat(String audioUrl) {
        return audioUrl.matches("(?i).+\\.(mp3|m4a|flac|ogg|wav)(\\?.*)?$");
    }

    /**
     * Extracts a query parameter value from a URL.
     *
     * @param url The URL to extract the parameter from.
     * @param paramName The name of the parameter to extract.
     * @return The value of the parameter, or null if not found.
     */
    private String extractQueryParam(String url, String paramName) {
        Matcher matcher = Pattern.compile("[?&]" + Pattern.quote(paramName) + "=([^&]*)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    // Dodaj klasę TrackJson
    private static class TrackJson {
        String id;
        String title;
        String artist;
        long duration;
        String audioUrl;
        String asin;
        String artworkUrl;
        String isrc;
    }

    // Klasa pomocnicza do zwracania audioUrl, artworkUrl i isrc
    private static class AudioUrlResult {
        public final String audioUrl;
        public final String artworkUrl;
        public final String isrc;
        public AudioUrlResult(String audioUrl, String artworkUrl, String isrc) {
            this.audioUrl = audioUrl;
            this.artworkUrl = artworkUrl;
            this.isrc = isrc;
        }
    }
}
