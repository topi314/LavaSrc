package com.github.topi314.lavasrc.spotify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public class SpotifyPartnerApiClient {
    public static final String PARTNER_API_BASE = "https://api-partner.spotify.com/pathfinder/v1/query";
    public static final String CLIENT_API_BASE = "https://spclient.wg.spotify.com/";
    private static final String TRACK_METADATA_ENDPOINT_PREFIX = CLIENT_API_BASE + "metadata/4/track/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.178 Spotify/1.2.65.255 Safari/537.36";
    private static final Logger log = LoggerFactory.getLogger(SpotifyPartnerApiClient.class);

    private final SpotifyTokenTracker tokenTracker;
    private final HttpInterface httpInterface;

    public SpotifyPartnerApiClient(SpotifyTokenTracker tokenTracker, HttpInterface httpInterface) {
        this.tokenTracker = tokenTracker;
        this.httpInterface = httpInterface;
    }

    private HttpPost createBaseRequest(ObjectNode extensions, ObjectNode variables, String operationName) throws IOException {
        var request = new HttpPost(PARTNER_API_BASE);

        request.setHeader("User-Agent", USER_AGENT);
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Authorization", "Bearer " + this.tokenTracker.getAnonymousAccessToken());
        request.setHeader("Spotify-App-Version", "1.2.80.289.gd6b01cc3");
        request.setHeader("Referer", "https://open.spotify.com/");

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode persistedQuery = mapper.createObjectNode();
        persistedQuery.put("version", 1);
        persistedQuery.put("sha256Hash", extensions.get("sha256Hash").asText());

        ObjectNode extensionsNode = mapper.createObjectNode();
        extensionsNode.set("persistedQuery", persistedQuery);

        ObjectNode body = mapper.createObjectNode();
        body.set("variables", variables);
        body.put("operationName", operationName);
        body.set("extensions", extensionsNode);

        String jsonBody = mapper.writeValueAsString(body);
        request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

        return request;
    }

    public JsonBrowser search(String query, int offset, int limit, boolean includeAudiobooks,
                            boolean includeArtistHasConcertsField, boolean includePreReleases,
                            boolean includeAuthors, int numberOfTopResults) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode variables = mapper.createObjectNode();
        variables.put("searchTerm", query);
        variables.put("offset", offset);
        variables.put("limit", limit);
        variables.put("numberOfTopResults", numberOfTopResults);
        variables.put("includeAudiobooks", includeAudiobooks);
        variables.put("includeArtistHasConcertsField", includeArtistHasConcertsField);
        variables.put("includePreReleases", includePreReleases);
        variables.put("includeAuthors", includeAuthors);

        ObjectNode extensions = mapper.createObjectNode();
        extensions.put("sha256Hash", "fcad5a3e0d5af727fb76966f06971c19cfa2275e6ff7671196753e008611873c");

        var request = createBaseRequest(extensions, variables, "searchDesktop");
        return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
    }

    public JsonBrowser getRecommendations(String uri) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode variables = mapper.createObjectNode();
        variables.put("uri", uri);

        ObjectNode extensions = mapper.createObjectNode();
        extensions.put("sha256Hash", "c77098ee9d6ee8ad3eb844938722db60570d040b49f41f5ec6e7be9160a7c86b");

        var request = createBaseRequest(extensions, variables, "internalLinkRecommenderTrack");
        return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
    }

    public JsonBrowser getTrack(String uri) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode variables = mapper.createObjectNode();
        variables.put("uri", uri);

        ObjectNode extensions = mapper.createObjectNode();
        extensions.put("sha256Hash", "612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294");

        var request = createBaseRequest(extensions, variables, "getTrack");
        return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
    }

    public JsonBrowser getPlaylist(String uri, int offset, int limit) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode variables = mapper.createObjectNode();
        variables.put("uri", uri);
        variables.put("offset", offset);
        variables.put("limit", limit);
        variables.put("enableWatchFeedEntrypoint", false);

        ObjectNode extensions = mapper.createObjectNode();
        extensions.put("sha256Hash", "bb67e0af06e8d6f52b531f97468ee4acd44cd0f82b988e15c2ea47b1148efc77");

        var request = createBaseRequest(extensions, variables, "fetchPlaylist");
        return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
    }

    public JsonBrowser getAlbum(String id, int offset, int limit) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode variables = mapper.createObjectNode();
        variables.put("uri", "spotify:album:" + id);
        variables.put("offset", offset);
        variables.put("limit", limit);
        ObjectNode extensions = mapper.createObjectNode();
        extensions.put("sha256Hash", "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10");
        HttpPost request = createBaseRequest(extensions, variables, "getAlbum");
        return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
    }

    @Nullable
    public String fetchIsrcViaSpClientMetadata(@NotNull String trackId) {
        if (isNullOrBlank(trackId)) {
            return null;
        }

        String gid;
        try {
            gid = spotifyIdToHex(trackId);
        } catch (IllegalArgumentException e) {
            log.debug("Failed to convert Spotify id to hex gid: {}", e.getMessage());
            return null;
        }

        var url = TRACK_METADATA_ENDPOINT_PREFIX + gid + "?market=from_token";
        var request = new HttpGet(url);
        request.setHeader("User-Agent", USER_AGENT);
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        try {
            request.setHeader("Authorization", "Bearer " + this.tokenTracker.getAnonymousAccessToken());
        } catch (IOException e) {
            log.debug("Failed to get anonymous Spotify token for metadata isrc fallback: {}", e.getMessage());
            return null;
        }

        try {
            var json = LavaSrcTools.fetchResponseAsJson(this.httpInterface, request);
            return parseIsrcFromSpClientMetadata(json);
        } catch (Exception e) {
            log.debug("Failed to fetch ISRC via spclient metadata: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isNullOrBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @Nullable
    private static String parseIsrcFromSpClientMetadata(@Nullable JsonBrowser json) {
        if (json == null) {
            return null;
        }
        JsonBrowser externalIds = json.get("external_id");
        if (externalIds == null || !externalIds.isList()) {
            return null;
        }
        for (JsonBrowser item : externalIds.values()) {
            if (item == null || item.isNull()) {
                continue;
            }
            String type = item.get("type").text();
            if ("isrc".equalsIgnoreCase(type)) {
                String id = item.get("id").text();
                return isNullOrBlank(id) ? null : id;
            }
        }
        return null;
    }

    /**
     * Converts Spotify base62 id to 16-byte hex gid used by Spotify internal APIs.
     */
    private static String spotifyIdToHex(@NotNull String base62Id) {
        final String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        BigInteger value = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(62);
        for (int i = 0; i < base62Id.length(); i++) {
            int idx = alphabet.indexOf(base62Id.charAt(i));
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid base62 char in Spotify id: " + base62Id.charAt(i));
            }
            value = value.multiply(base).add(BigInteger.valueOf(idx));
        }
        String hex = value.toString(16);
        if (hex.length() > 32) {
            hex = hex.substring(hex.length() - 32);
        }
        return "0".repeat(32 - hex.length()) + hex;
    }
}
