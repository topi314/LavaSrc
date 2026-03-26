package com.github.topi314.lavasrc.spotify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public class SpotifyRequestPayload {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String SEARCH_OPERATION = "searchDesktop";
	private static final String SEARCH_HASH = "fcad5a3e0d5af727fb76966f06971c19cfa2275e6ff7671196753e008611873c";

	private static final String RECOMMENDATIONS_OPERATION = "internalLinkRecommenderTrack";
	private static final String RECOMMENDATIONS_HASH = "c77098ee9d6ee8ad3eb844938722db60570d040b49f41f5ec6e7be9160a7c86b";

	private static final String TRACK_OPERATION = "getTrack";
	private static final String TRACK_HASH = "612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294";

	private static final String PLAYLIST_OPERATION = "fetchPlaylist";
	private static final String PLAYLIST_HASH = "bb67e0af06e8d6f52b531f97468ee4acd44cd0f82b988e15c2ea47b1148efc77";

	private static final String ALBUM_OPERATION = "getAlbum";
	private static final String ALBUM_HASH = "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10";

	private final String operationName;
	private final String sha256Hash;
	private final ObjectNode variables;

	public SpotifyRequestPayload(String operationName, String sha256Hash) {
		this.operationName = operationName;
		this.sha256Hash = sha256Hash;
		this.variables = MAPPER.createObjectNode();
	}

	public SpotifyRequestPayload withVariable(String key, Object value) {
		this.variables.set(key, MAPPER.valueToTree(value));
		return this;
	}

	public String serialize() throws IOException {
		ObjectNode persistedQuery = MAPPER.createObjectNode();
		persistedQuery.put("version", 1);
		persistedQuery.put("sha256Hash", this.sha256Hash);

		ObjectNode extensionsNode = MAPPER.createObjectNode();
		extensionsNode.set("persistedQuery", persistedQuery);

		ObjectNode body = MAPPER.createObjectNode();
		body.set("variables", this.variables);
		body.put("operationName", this.operationName);
		body.set("extensions", extensionsNode);

		return MAPPER.writeValueAsString(body);
	}

	public static SpotifyRequestPayload forSearch(String query, int offset, int limit, boolean includeAudiobooks, boolean includeArtistHasConcertsField, boolean includePreReleases, boolean includeAuthors, int numberOfTopResults) {
		return new SpotifyRequestPayload(SEARCH_OPERATION, SEARCH_HASH)
			.withVariable("searchTerm", query)
			.withVariable("offset", offset)
			.withVariable("limit", limit)
			.withVariable("numberOfTopResults", numberOfTopResults)
			.withVariable("includeAudiobooks", includeAudiobooks)
			.withVariable("includeArtistHasConcertsField", includeArtistHasConcertsField)
			.withVariable("includePreReleases", includePreReleases)
			.withVariable("includeAuthors", includeAuthors);
	}

	public static SpotifyRequestPayload forRecommendations(String uri) {
		return new SpotifyRequestPayload(RECOMMENDATIONS_OPERATION, RECOMMENDATIONS_HASH)
			.withVariable("uri", uri);
	}

	public static SpotifyRequestPayload forTrack(String uri) {
		return new SpotifyRequestPayload(TRACK_OPERATION, TRACK_HASH)
			.withVariable("uri", uri);
	}

	public static SpotifyRequestPayload forPlaylist(String uri, int offset, int limit) {
		return new SpotifyRequestPayload(PLAYLIST_OPERATION, PLAYLIST_HASH)
			.withVariable("uri", uri)
			.withVariable("offset", offset)
			.withVariable("limit", limit)
			.withVariable("enableWatchFeedEntrypoint", false);
	}

	public static SpotifyRequestPayload forAlbum(String id, int offset, int limit) {
		return new SpotifyRequestPayload(ALBUM_OPERATION, ALBUM_HASH)
			.withVariable("uri", "spotify:album:" + id)
			.withVariable("offset", offset)
			.withVariable("limit", limit);
	}
}
