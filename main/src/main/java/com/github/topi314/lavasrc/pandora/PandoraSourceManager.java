package com.github.topi314.lavasrc.pandora;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
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
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class PandoraSourceManager extends MirroringAudioSourceManager implements HttpConfigurable, AudioSearchManager {

    public static final Pattern URL_PATTERN = Pattern.compile("^@?(?:https?://)?(?:www\\.)?pandora\\.com/(?:playlist/(?<id>PL:[\\d:]+)|artist/[\\w\\-]+(?:/[\\w\\-]+)*/(?<id2>(?:TR|AL|AR)[A-Za-z0-9]+))(?:[?#].*)?$");
    public static final String BASE_URL = "https://www.pandora.com";
    public static final String SEARCH_PREFIX = "pdsearch:";
    public static final String RECOMMENDATIONS_PREFIX = "pdrec:";
    private static final String ENDPOINT_SEARCH = "/api/v3/sod/search";
    private static final String ENDPOINT_ANNOTATE = "/api/v4/catalog/annotateObjects";
    private static final String ENDPOINT_DETAILS = "/api/v4/catalog/getDetails";
    private static final String ENDPOINT_PLAYLIST_TRACKS = "/api/v7/playlists/getTracks";
    private static final String ENDPOINT_ARTIST_ALL_TRACKS = "/api/v4/catalog/getAllArtistTracksWithCollaborations";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    private static final Logger log = LoggerFactory.getLogger(PandoraSourceManager.class);
    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    private String cookie;
    private String csrfToken;
    private String authToken;
    private int searchLimit = 6;
    public static final java.util.Set<AudioSearchResult.Type> SEARCH_TYPES = java.util.Set.of(AudioSearchResult.Type.ALBUM, AudioSearchResult.Type.ARTIST, AudioSearchResult.Type.PLAYLIST, AudioSearchResult.Type.TRACK);

    public PandoraSourceManager(String[] providers, String cookie, String csrfToken, String authToken, Function<Void, AudioPlayerManager> audioPlayerManager, int searchLimit) {
        this(cookie, csrfToken, authToken, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers), searchLimit);
    }

    public PandoraSourceManager(String[] providers, String cookie, String csrfToken, String authToken, Function<Void, AudioPlayerManager> audioPlayerManager) {
        this(cookie, csrfToken, authToken, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers), 6);
    }

    public PandoraSourceManager(String cookie, String csrfToken, String authToken, Function<Void, AudioPlayerManager> audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver, int searchLimit) {
        super(audioPlayerManager, mirroringAudioTrackResolver);
        if (cookie == null || cookie.isEmpty()) {
            throw new IllegalArgumentException("Pandora cookie must be set");
        }
        if (csrfToken == null || csrfToken.isEmpty()) {
            throw new IllegalArgumentException("Pandora csrf token must be set");
        }
        if (authToken == null || authToken.isEmpty()) {
            throw new IllegalArgumentException("Pandora auth token must be set");
        }
        this.cookie = cookie;
        this.csrfToken = csrfToken;
        this.authToken = authToken;
        this.searchLimit = searchLimit > 0 ? searchLimit : 6;
    }

	@Override
	public AudioSearchResult loadSearch(@org.jetbrains.annotations.NotNull String query, @org.jetbrains.annotations.NotNull java.util.Set<AudioSearchResult.Type> types) {
		try {
			if (query.startsWith(SEARCH_PREFIX)) {
				return this.getAutocomplete(query.substring(SEARCH_PREFIX.length()), types);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit > 0 ? searchLimit : 6;
    }

    public void setCookie(String cookie) {
        if (cookie != null && !cookie.isEmpty()) {
            this.cookie = cookie;
        }
    }

    public void setCsrfToken(String csrfToken) {
        if (csrfToken != null && !csrfToken.isEmpty()) {
            this.csrfToken = csrfToken;
        }
    }

    public void setAuthToken(String authToken) {
        if (authToken != null && !authToken.isEmpty()) {
            this.authToken = authToken;
        }
    }

    @Override
    public String getSourceName() {
        return "pandora";
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        var extendedAudioTrackInfo = super.decodeTrack(input);
        return new PandoraAudioTrack(trackInfo,
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
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference == null || reference.identifier == null) {
            return null;
        }
        var identifier = reference.identifier;
        try {
            if (identifier.startsWith(SEARCH_PREFIX)) {
                var query = identifier.substring(SEARCH_PREFIX.length());
                if (query.isEmpty()) {
                    throw new IllegalArgumentException("No query provided for search");
                }
                return this.getSearch(query);
            }

            if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
                var trackId = identifier.substring(RECOMMENDATIONS_PREFIX.length());
                if (trackId.isEmpty()) {
					throw new IllegalArgumentException("No track ID provided for recommendations");
				}
                return this.getRecommendations(trackId);
            }

            var input = identifier.trim();
            var matcher = URL_PATTERN.matcher(input);
            if (matcher.find()) {
                String id = matcher.group("id") != null ? matcher.group("id") : matcher.group("id2");
                if (id == null || id.isEmpty()) {
                    return null;
                }

                if (id.startsWith("TR")) {
                    return this.getTrack(id);
                } else if (id.startsWith("AL")) {
                    return this.getAlbum(id);
                } else if (id.startsWith("AR")) {
                    if (input.contains("/artist/all-songs/")) {
                        return this.getArtistAllSongs(id);
                    }
                    return this.getArtist(id);
                } else if (id.startsWith("PL:")) {
                    return this.getPlaylist(id);
                }
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private JsonBrowser postJson(String path, String body) throws IOException {
        var post = new HttpPost(BASE_URL + path);
        post.setHeader("Accept", "application/json, text/plain, */*");
        post.setHeader("accept-language", "en-US,en;q=0.9");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("origin", BASE_URL);
        post.setHeader("sec-fetch-mode", "cors");
        post.setHeader("sec-fetch-site", "same-origin");
        post.setHeader("Cookie", this.cookie);
        post.setHeader("X-Csrftoken", this.csrfToken);
        post.setHeader("X-Authtoken", this.authToken);
        post.setHeader("User-Agent", USER_AGENT);
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
        var json = LavaSrcTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), post);
        return json;
    }

    private String getArtworkUrl(JsonBrowser node) {
        JsonBrowser icon = node.get("icon");
        if (!icon.isNull()) {
            String artId = icon.get("artId").text();
            if (artId != null && !artId.isEmpty()) {
                return "https://content-images.p-cdn.com/" + artId + "_1080W_1080H.jpg";
            }
        }
        
        String thorLayers = node.get("thorLayers").text();
        if (thorLayers != null && !thorLayers.isEmpty()) {
            if (thorLayers.startsWith("_;grid")) {
                String encodedLayers = URLEncoder.encode(thorLayers, StandardCharsets.UTF_8);
                return "https://dyn-images.p-cdn.com/?l=" + encodedLayers + "&w=1080&h=1080";
            }
            return "https://content-images.p-cdn.com/" + thorLayers + "_1080W_1080H.jpg";
        }
        
        return null;
    }

    private AudioTrack mapTrack(JsonBrowser track, JsonBrowser annotations) {
        var title = track.get("name").text();
        if (title == null || title.isEmpty()) {
            return null;
        }
        var author = track.get("artistName").text();
        if (author == null || author.isEmpty()) author = "unknown";
        var duration = track.get("duration").asLong(0) * 1000;
        if (duration == 0) {
			return null;
		}
        var id = track.get("pandoraId").text();
        var urlPath = track.get("shareableUrlPath").text();
        var isrc = track.get("isrc").text();

        var albumId = track.get("albumId").text();
        var album = annotations.get(albumId);
        var albumName = album.get("name").text();
        var albumUrl = album.get("shareableUrlPath").text();

        var artistId = track.get("artistId").text();
        var artist = annotations.get(artistId);
        var artistUrl = artist.get("shareableUrlPath").text();
        var artistArtworkUrl = getArtworkUrl(artist);

        String originalUrl = urlPath != null ? BASE_URL + urlPath : null;
        String artworkUrl = getArtworkUrl(track);
        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, id, false, originalUrl, artworkUrl, isrc);
        return new PandoraAudioTrack(info, albumName, albumUrl != null ? BASE_URL + albumUrl : null, artistUrl != null ? BASE_URL + artistUrl : null, artistArtworkUrl, null, false, this);
    }

    private String buildAnnotateRequest(List<String> pandoraIds) {
        StringBuilder ids = new StringBuilder("{\"pandoraIds\":[");
        for (int i = 0; i < pandoraIds.size(); i++) {
            if (i > 0) ids.append(',');
            ids.append('"').append(escape(pandoraIds.get(i))).append('"');
        }
        ids.append("]}");
        return ids.toString();
    }

    private AudioItem getRecommendations(String trackId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(trackId) + "\"}";
        var details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) {
            return AudioReference.NO_TRACK;
        }
        var similar = details.get("trackDetails").get("similarTracks");
        if (similar.isNull() || similar.values().isEmpty()) return AudioReference.NO_TRACK;
        List<String> idList = new ArrayList<>();
        for (var v : similar.values()) {
            idList.add(v.text());
        }
        var annotations = postJson(ENDPOINT_ANNOTATE, buildAnnotateRequest(idList));
        List<AudioTrack> tracks = new ArrayList<>();
        for (var v : similar.values()) {
            var item = annotations.get(v.text());
            if (item.isNull()) continue;
            var track = mapTrack(item, annotations);
            if (track != null) tracks.add(track);
        }
        return new PandoraAudioPlaylist("Pandora recommendations", tracks, ExtendedAudioPlaylist.Type.RECOMMENDATIONS, null, null, null, tracks.size());
    }

    private ExtendedAudioPlaylist parseAlbum(JsonBrowser album, JsonBrowser annotations) {
        var name = album.get("name").text();
        var tracksArray = album.get("tracks");
        List<AudioTrack> tracks = new ArrayList<>();
        if (!tracksArray.isNull()) {
            for (var v : tracksArray.values()) {
                var t = annotations.get(v.text());
                if (t.isNull()) continue;
                var at = mapTrack(t, annotations);
                if (at != null) tracks.add(at);
            }
        }
        var url = album.get("shareableUrlPath").text();
        var artworkUrl = getArtworkUrl(album);
        Integer total = album.get("trackCount").isNull() ? tracks.size() : (int) album.get("trackCount").asLong( tracks.size());
        return new PandoraAudioPlaylist(name, tracks, ExtendedAudioPlaylist.Type.ALBUM, url != null ? BASE_URL + url : null, artworkUrl, album.get("artistName").text(), total);
    }

    private ExtendedAudioPlaylist parseArtist(JsonBrowser artist, JsonBrowser detailsRoot) {
        var name = artist.get("name").text();
        List<AudioTrack> tracks = new ArrayList<>();
        var artistDetails = detailsRoot.get("artistDetails");
        var top = artistDetails.get("topTracks");
        var annotations = detailsRoot.get("annotations");
        if (!top.isNull()) {
            for (var v : top.values()) {
                var t = annotations.get(v.text());
                if (t.isNull()) continue;
                var at = mapTrack(t, annotations);
                if (at != null) tracks.add(at);
            }
        }
        var url = artist.get("shareableUrlPath").text();
        var artworkUrl = getArtworkUrl(artist);
        return new PandoraAudioPlaylist(name + "'s Top Tracks", tracks, ExtendedAudioPlaylist.Type.ARTIST, url != null ? BASE_URL + url : null, artworkUrl, name, tracks.size());
    }

    private ExtendedAudioPlaylist getPlaylist(String playlistId) throws IOException {
        var request = JsonBrowser.parse("{}");
        var reqObj = JsonBrowser.parse("{}");
        reqObj.put("pandoraId", playlistId);
        reqObj.put("playlistVersion", 0);
        reqObj.put("offset", 0);
        reqObj.put("limit", 5000);
        reqObj.put("annotationLimit", 100);
        reqObj.put("allowedTypes", JsonBrowser.parse("[\"TR\"]"));
        reqObj.put("bypassPrivacyRules", true);
        request.put("request", reqObj);

        var json = postJson(ENDPOINT_PLAYLIST_TRACKS, request.format());
        var annotations = json.get("annotations");
        var tracksNode = json.get("tracks");

        Map<String, JsonBrowser> merged = new HashMap<>();
        for (var v : annotations.values()) {
            var id = v.get("pandoraId").text();
            if (id != null && !id.isEmpty()) {
                merged.put(id, v);
            }
        }

        List<String> allIds = new ArrayList<>();
        for (var t : tracksNode.values()) {
            var id = t.get("pandoraId").text();
            if (id != null && !id.isEmpty()) {
                allIds.add(id);
            }
        }

        List<String> missing = new ArrayList<>();
        for (var id : allIds) {
            if (!merged.containsKey(id)) {
                missing.add(id);
            }
        }

        if (!missing.isEmpty()) {
            var extra = postJson(ENDPOINT_ANNOTATE, buildAnnotateRequest(missing));
            for (var id : missing) {
                var node = extra.get(id);
                if (!node.isNull()) {
                    merged.put(id, node);
                }
            }
        }

        List<AudioTrack> tracks = new ArrayList<>();
        var mergedBrowser = JsonBrowser.parse("{}");
        for (var entry : merged.entrySet()) {
            mergedBrowser.put(entry.getKey(), entry.getValue());
        }

        for (var t : tracksNode.values()) {
            var id = t.get("pandoraId").text();
            var ann = merged.get(id);
            if (ann == null) continue;
            var at = mapTrack(ann, mergedBrowser);
            if (at != null) tracks.add(at);
        }
        var name = json.get("name").text();
        var path = json.get("shareableUrlPath").text();
        var artworkUrl = getArtworkUrl(json);

        String authorName = null;
        var listenerId = json.get("listenerPandoraId").text();
        if (listenerId != null) {
            var author = annotations.get(listenerId);
            if (!author.isNull()) {
                authorName = author.get("fullname").text();
            }
        }
        
        return new PandoraAudioPlaylist(name, tracks, ExtendedAudioPlaylist.Type.PLAYLIST, path != null ? BASE_URL + path : null, artworkUrl, authorName, tracks.size());
    }

    public AudioItem getTrack(String trackId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(trackId) + "\"}";
        var details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) return AudioReference.NO_TRACK;
        var annotations = details.get("annotations");
        var track = findByUrlSuffix(trackId, annotations);
        if (track.isNull()) {
            return AudioReference.NO_TRACK;
        }
        var at = mapTrack(track, annotations);
        return at != null ? at : AudioReference.NO_TRACK;
    }

    public AudioItem getAlbum(String albumId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(albumId) + "\"}";
        var details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) return AudioReference.NO_TRACK;
        var annotations = details.get("annotations");
        var album = findByUrlSuffix(albumId, annotations);
        if (album.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return parseAlbum(album, annotations);
    }

    public AudioItem getArtist(String artistId) throws IOException {
        String detailsBody = "{\"pandoraId\":\"" + escape(artistId) + "\"}";
        var details = postJson(ENDPOINT_DETAILS, detailsBody);
        if (details == null) return AudioReference.NO_TRACK;
        var annotations = details.get("annotations");
        var artist = findByUrlSuffix(artistId, annotations);
        if (artist.isNull()) {
            return AudioReference.NO_TRACK;
        }
        return parseArtist(artist, details);
    }

    public AudioItem getArtistAllSongs(String artistId) throws IOException {
        String body = "{\"artistPandoraId\":\"" + escape(artistId) + "\",\"annotationLimit\":100}";
        var json = postJson(ENDPOINT_ARTIST_ALL_TRACKS, body);
        if (json == null) return AudioReference.NO_TRACK;

        var annotations = json.get("annotations");
        var tracksNode = json.get("tracks");
        if (tracksNode.isNull() || tracksNode.values().isEmpty()) return AudioReference.NO_TRACK;

        Map<String, JsonBrowser> merged = new HashMap<>();
        for (var v : annotations.values()) {
            var pid = v.get("pandoraId").text();
            if (pid != null && !pid.isEmpty()) {
                merged.put(pid, v);
            }
        }

        List<String> allTrackIds = new ArrayList<>();
        for (var t : tracksNode.values()) {
            var tid = t.text();
            if (tid != null && !tid.isEmpty()) allTrackIds.add(tid);
        }

        List<String> missing = new ArrayList<>();
        for (var tid : allTrackIds) {
            if (!merged.containsKey(tid)) missing.add(tid);
        }
        if (!missing.isEmpty()) {
            var extra = postJson(ENDPOINT_ANNOTATE, buildAnnotateRequest(missing));
            for (var tid : missing) {
                var node = extra.get(tid);
                if (!node.isNull()) merged.put(tid, node);
            }
        }

        var mergedBrowser = JsonBrowser.parse("{}");
        for (var entry : merged.entrySet()) {
            mergedBrowser.put(entry.getKey(), entry.getValue());
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (var tid : allTrackIds) {
            var ann = merged.get(tid);
            if (ann == null) continue;
            var at = mapTrack(ann, mergedBrowser);
            if (at != null) tracks.add(at);
        }
        
        JsonBrowser artist = findByUrlSuffix(artistId, annotations);
        if (artist.isNull()) {
            String detailsBody = "{\"pandoraId\":\"" + escape(artistId) + "\"}";
            var details = postJson(ENDPOINT_DETAILS, detailsBody);
            if (details != null) {
                var detailsAnn = details.get("annotations");
                var match = findByUrlSuffix(artistId, detailsAnn);
                if (!match.isNull()) artist = match;
            }
        }
        String name = artist.isNull() ? "All Songs" : (artist.get("name").safeText() + " - All Songs");
        String path = artist.isNull() ? null : artist.get("shareableUrlPath").text();
        String artworkUrl = artist.isNull() ? null : getArtworkUrl(artist);
        String authorName = artist.isNull() ? null : artist.get("name").text();

        return new PandoraAudioPlaylist(name, tracks, ExtendedAudioPlaylist.Type.ARTIST, path != null ? BASE_URL + path : null, artworkUrl, authorName, tracks.size());
    }

    private JsonBrowser findByUrlSuffix(String urlTail, JsonBrowser annotations) {
        for (var value : annotations.values()) {
            var path = value.get("shareableUrlPath").text();
            if (path != null && path.endsWith("/" + urlTail)) {
                return value;
            }
            var slug = value.get("slugPlusPandoraId").text();
            if (slug != null && (slug.endsWith(urlTail) || slug.contains(urlTail))) {
                return value;
            }
        }
        return JsonBrowser.NULL_BROWSER;
    }

    public AudioItem getSearch(String query) throws IOException {
        var request = new StringBuilder();
        request.append('{')
            .append("\"query\":\"").append(escape(query)).append("\",")
            .append("\"types\":[\"TR\",\"AL\",\"AR\",\"PL\"],")
            .append("\"listener\":null,")
            .append("\"start\":0,")
            .append("\"count\":100,")
            .append("\"annotate\":true,")
            .append("\"annotationRecipe\":\"CLASS_OF_2019\"")
            .append('}');

        var json = postJson(ENDPOINT_SEARCH, request.toString());
        if (json == null) return AudioReference.NO_TRACK;
        var annotations = json.get("annotations");
        var results = json.get("results");
        if (results.isNull() || results.values().isEmpty()) return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        int added = 0;
        for (var v : results.values()) {
            var item = annotations.get(v.text());
            if (item.isNull()) continue;
            if (!"TR".equals(item.get("type").text())) continue;
            var at = mapTrack(item, annotations);
            if (at != null) {
                tracks.add(at);
                if (++added >= this.searchLimit) break;
            }
        }
        if (tracks.isEmpty()) return AudioReference.NO_TRACK;
        return new BasicAudioPlaylist("Pandora Search: " + query, tracks, null, true);
    }

    private AudioSearchResult getAutocomplete(String query, java.util.Set<AudioSearchResult.Type> types) throws IOException {
        final int limit = this.searchLimit;
        if (types.isEmpty()) {
            types = SEARCH_TYPES;
        }

        List<String> typeKeys = new ArrayList<>();
        if (types.contains(AudioSearchResult.Type.TRACK)) typeKeys.add("TR");
        if (types.contains(AudioSearchResult.Type.ALBUM)) typeKeys.add("AL");
        if (types.contains(AudioSearchResult.Type.ARTIST)) typeKeys.add("AR");
        if (types.contains(AudioSearchResult.Type.PLAYLIST)) typeKeys.add("PL");

        var sb = new StringBuilder();
        sb.append('{')
            .append("\"query\":\"").append(escape(query)).append("\",")
            .append("\"types\":[");
        for (int i = 0; i < typeKeys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("\"").append(typeKeys.get(i)).append("\"");
        }
        sb.append("],")
            .append("\"listener\":null,")
            .append("\"start\":0,")
            .append("\"count\":100,")
            .append("\"annotate\":true,")
            .append("\"annotationRecipe\":\"CLASS_OF_2019\"")
            .append('}');

        var json = postJson(ENDPOINT_SEARCH, sb.toString());
        if (json == null) {
            return AudioSearchResult.EMPTY;
        }
        var annotations = json.get("annotations");
        var results = json.get("results");

        var albums = new ArrayList<AudioPlaylist>();
        var artists = new ArrayList<AudioPlaylist>();
        var playlists = new ArrayList<AudioPlaylist>();
        var tracks = new ArrayList<AudioTrack>();

        for (var idNode : results.values()) {
            var id = idNode.text();
            var item = annotations.get(id);
            if (item.isNull()) continue;
            var type = item.get("type").text();
            if ("TR".equals(type) && types.contains(AudioSearchResult.Type.TRACK)) {
                var at = mapTrack(item, annotations);
                if (at != null) tracks.add(at);
            } else if ("AL".equals(type) && types.contains(AudioSearchResult.Type.ALBUM)) {
                var name = item.get("name").safeText();
                var path = item.get("shareableUrlPath").text();
                var artwork = getArtworkUrl(item);
                var artistName = item.get("artistName").text();
                albums.add(new PandoraAudioPlaylist(name, java.util.Collections.emptyList(), ExtendedAudioPlaylist.Type.ALBUM, path != null ? BASE_URL + path : null, artwork, artistName, (int) item.get("trackCount").asLong(0)));
            } else if ("AR".equals(type) && types.contains(AudioSearchResult.Type.ARTIST)) {
                var name = item.get("name").safeText() + "'s Top Tracks";
                var path = item.get("shareableUrlPath").text();
                var artwork = getArtworkUrl(item);
                var author = item.get("name").text();
                artists.add(new PandoraAudioPlaylist(name, java.util.Collections.emptyList(), ExtendedAudioPlaylist.Type.ARTIST, path != null ? BASE_URL + path : null, artwork, author, null));
            } else if ("PL".equals(type) && types.contains(AudioSearchResult.Type.PLAYLIST)) {
                var name = item.get("name").safeText();
                var path = item.get("shareableUrlPath").text();
                var artwork = getArtworkUrl(item);
                String authorName = null;
                var listenerId = item.get("listenerPandoraId").text();
                if (listenerId != null) {
                    var author = annotations.get(listenerId);
                    if (!author.isNull()) {
                        authorName = author.get("fullname").text();
                    }
                }
                
                playlists.add(new PandoraAudioPlaylist(name, java.util.Collections.emptyList(), ExtendedAudioPlaylist.Type.PLAYLIST, path != null ? BASE_URL + path : null, artwork, authorName, (int) item.get("totalTracks").asLong(0)));
            }
        }

        if (tracks.size() > limit) {
            tracks = new ArrayList<>(tracks.subList(0, limit));
        }
        return new BasicAudioSearchResult(tracks, albums, artists, playlists, new ArrayList<>());
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		httpInterfaceManager.configureBuilder(configurator);
	}

	@Override
	public void shutdown() {
		try {
			httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	public HttpInterface getHttpInterface() {
		return httpInterfaceManager.getInterface();
	}
}