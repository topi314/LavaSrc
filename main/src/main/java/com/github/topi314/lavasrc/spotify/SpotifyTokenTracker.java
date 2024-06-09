package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasrc.LavaSrcTools;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

public class SpotifyTokenTracker {
	private static final Logger log = LoggerFactory.getLogger(SpotifyTokenTracker.class);

	private final SpotifySourceManager sourceManager;
	private final String clientId;
	private final String clientSecret;

	private String accessToken;
	private Instant expires;

	public SpotifyTokenTracker(SpotifySourceManager source, String clientId, String clientSecret) {
		this.sourceManager = source;
		this.clientId = clientId;
		this.clientSecret = clientSecret;

		if (!hasValidCredentials()) {
			log.info("Missing/invalid credentials, falling back to public token.");
		}
	}

	public String getAccessToken() {
		if (accessToken == null || expires == null || expires.isBefore(Instant.now())) {
			synchronized (this) {
				if (accessToken == null || expires == null || expires.isBefore(Instant.now())) {
					refreshAccessToken();
				}
			}
		}

		return accessToken;
	}

	private void refreshAccessToken() {
		boolean usePublicToken = !hasValidCredentials();
		HttpUriRequest request;

		if (!usePublicToken) {
			request = new HttpPost("https://accounts.spotify.com/api/token");
			request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((this.clientId + ":" + this.clientSecret).getBytes(StandardCharsets.UTF_8)));
			((HttpPost) request).setEntity(new UrlEncodedFormEntity(List.of(new BasicNameValuePair("grant_type", "client_credentials")), StandardCharsets.UTF_8));
		} else {
			request = new HttpGet("https://open.spotify.com/get_access_token");
		}

		try {
			var json = LavaSrcTools.fetchResponseAsJson(sourceManager.getHttpInterface(), request);

			if (!json.get("error").isNull()) {
				String error = json.get("error").text();
				throw new RuntimeException(error);
			}

			if (!usePublicToken) {
				accessToken = json.get("access_token").text();
				expires = Instant.now().plusSeconds(json.get("expires_in").asLong(0));
			} else {
				accessToken = json.get("accessToken").text();
				expires = Instant.ofEpochMilli(json.get("accessTokenExpirationTimestampMs").asLong(0));
			}
		} catch (IOException e) {
			throw new RuntimeException("Access token refreshing failed", e);
		}
	}

	private boolean hasValidCredentials() {
		return clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty();
	}
}
