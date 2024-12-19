package com.github.topi314.lavasrc.applemusic;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

public class AppleMusicTokenManager {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("ey[\\w-]+\\.[\\w-]+\\.[\\w-]+");

	private Token token;

	public AppleMusicTokenManager(String mediaAPIToken) throws IOException {
		if (mediaAPIToken == null || mediaAPIToken.isEmpty()) {
			this.fetchNewToken();
		} else {
			this.parseTokenData(mediaAPIToken);
		}
	}

	public Token getToken() throws IOException {
		if (this.token.isExpired()) {
			this.fetchNewToken();
		}
		return this.token;
	}

	public void setToken(String mediaAPIToken) throws IOException {
		this.parseTokenData(mediaAPIToken);
	}

	private void parseTokenData(String mediaAPIToken) throws IOException {
		if (mediaAPIToken == null || mediaAPIToken.isEmpty()) {
			throw new IllegalArgumentException("Invalid token provided.");
		}

		var parts = mediaAPIToken.split("\\.");
		if (parts.length < 3) {
			throw new IllegalArgumentException("Invalid token provided must have 3 parts separated by '.'");
		}

		var payload = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
		var json = JsonBrowser.parse(payload);

		this.token = new Token(mediaAPIToken, json.get("root_https_origin").index(0).text(), Instant.ofEpochSecond(json.get("exp").asLong(0)));
	}

	private void fetchNewToken() throws IOException {
		try (var httpClient = HttpClients.createDefault()) {
			var mainPageHtml = fetchHtml(httpClient, "https://music.apple.com");
			var tokenScriptUrl = extractTokenScriptUrl(mainPageHtml);

			if (tokenScriptUrl == null) {
				throw new IllegalStateException("Failed to locate token script URL.");
			}

			var tokenScriptContent = fetchHtml(httpClient, tokenScriptUrl);
			var tokenMatcher = TOKEN_PATTERN.matcher(tokenScriptContent);

			if (!tokenMatcher.find()) {
				throw new IllegalStateException("Failed to extract token from script content.");
			}
			this.parseTokenData(tokenMatcher.group());
		}
	}

	private String fetchHtml(CloseableHttpClient httpClient, String url) throws IOException {
		var request = new HttpGet(url);
		try (var response = httpClient.execute(request)) {
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new IOException("Failed to fetch URL: " + url + ". Status code: " + response.getStatusLine().getStatusCode());
			}
			return IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
		}
	}

	private String extractTokenScriptUrl(String html) {
		var document = Jsoup.parse(html, "https://music.apple.com");
		return document.select("script[type=module][src~=/assets/index.*.js]")
			.stream()
			.findFirst()
			.map(element -> "https://music.apple.com" + element.attr("src"))
			.orElseThrow(() -> new IllegalStateException("Failed to find token script URL in the provided HTML."));
	}

	public static class Token {
		public final String apiToken;
		public final String origin;
		public final Instant expire;

		public Token(String apiToken, String origin, Instant expire) {
			this.apiToken = apiToken;
			this.origin = origin;
			this.expire = expire;
		}

		private boolean isExpired() {
			if (this.apiToken == null || this.expire == null) {
				return true;
			}
			return expire.minusSeconds(5).isBefore(Instant.now());
		}
	}
}
