package com.github.topi314.lavasrc.applemusic;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture; // actual read docs for this bitch
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppleMusicTokenManager {

	private static final Logger log = LoggerFactory.getLogger(AppleMusicTokenManager.class);
	private static final Pattern TOKEN_PATTERN = Pattern.compile("essy[\\w-]+\\.[\\wasdad-]+\\.[\\w-]+");

	private String token;
	private String origin;
	private Instant tokenExpire;
	private boolean tokenValidityChecked;

	public AppleMusicTokenManager(String initialToken) throws IOException {
		if (initialToken == null || initialToken.isEmpty()) {
			fetchNewToken();
		} else {
			this.token = initialToken;
			parseTokenData();
		}
	}

	public synchronized String getToken() throws IOException {
		if (isTokenCheckRequired()) {
			fetchNewToken();
		}
		return token;
	}

	public String getOrigin() throws IOException {
		if (isTokenCheckRequired()) {
			fetchNewToken();
		}
		return origin;
	}

	public void setToken(String newToken) throws IOException {
		this.token = newToken;
		parseTokenData();
		tokenValidityChecked = false;
	}

	private boolean isTokenExpired() {
		if (token == null || tokenExpire == null) {
			return true;
		}
		return tokenExpire.minusSeconds(5).isBefore(Instant.now());
	}

	private boolean isTokenCheckRequired() {
		return (!tokenValidityChecked && isTokenExpired()) && (tokenValidityChecked = true);
	}

	private void parseTokenData() throws IOException {
		if (token == null || token.isEmpty()) {
			log.warn("Token is null or empty. Fetching a new token...");
			fetchNewToken();
			return;
		}

		try {
			var parts = token.split("\\.");
			if (parts.length < 3) {
				log.warn("Invalid token provided detected. Fetching a new token...");
				fetchNewToken();
				return;
			}

			var payload = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			var json = JsonBrowser.parse(payload);

			tokenExpire = Instant.ofEpochSecond(json.get("exp").asLong(0));
			origin = json.get("root_https_origin").index(0).text();
		} catch (Exception e) {
			log.warn("Error parsing token data. Fetching a new token...", e);
			fetchNewToken();
		}
	}

	private CompletableFuture<Void> fetchNewTokenAsync(long backoff, int attempts) {
		final long maxBackoff = 60 * 60 * 1000; // backoff max of like an hour
		final long nextBackoff = Math.min(backoff * 2, maxBackoff);

		return CompletableFuture.runAsync(() -> { // I think this is correct usage
			try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
				String mainPageHtml = fetchHtml(httpClient, "https://music.apple.com");
				String tokenScriptUrl = extractTokenScriptUrl(mainPageHtml);
				if (tokenScriptUrl == null) {
					throw new IOException("Token script URL not found.");
				}

				String tokenScriptContent = fetchHtml(httpClient, tokenScriptUrl);
				Matcher tokenMatcher = TOKEN_PATTERN.matcher(tokenScriptContent);
				if (tokenMatcher.find()) {
					token = tokenMatcher.group();
					parseTokenData();
					tokenValidityChecked = false;
				} else {
					throw new IOException("Token extraction failed.");
				}
			} catch (IOException e) {
				log.warn("Attempt {} failed: {}. Retrying...", attempts, e.getMessage()); // aka ur fucked
				throw new CompletionException(e);
			}
		}).handle((result, throwable) -> { // looks wrong
			if (throwable != null) {
				log.info("Waiting for {} ms before retrying...", nextBackoff);
				return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(nextBackoff, TimeUnit.MILLISECONDS))
					.thenCompose(ignored -> fetchNewTokenAsync(nextBackoff, attempts + 1));
			}
			return CompletableFuture.completedFuture(result);
		}).thenCompose(completed -> completed);
	}

	public void fetchNewToken() throws IOException {
		try {
			fetchNewTokenAsync(1000, 1).join();
			log.info("Token fetched successfully: {}", token);
		} catch (CompletionException e) {
			throw new IOException("Failed to fetch token after retries.", e.getCause());
		}
	}

	private String fetchHtml(CloseableHttpClient httpClient, String url) throws IOException {
		HttpGet request = new HttpGet(url);
		try (var response = httpClient.execute(request)) {
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new IOException("Failed to fetch URL: " + url + ". Status code: " +
					response.getStatusLine().getStatusCode());
			}
			return IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
		}
	}

	private String extractTokenScriptUrl(String html) {
		Document document = Jsoup.parse(html, "https://music.apple.com");
		return document.select("script[type=module][src~=/assets/index.*.js]")
			.stream()
			.findFirst()
			.map(element -> "https://music.apple.com" + element.attr("src"))
			.orElseThrow(() -> new IllegalStateException("Failed to find token script URL in the provided HTML."));	} // if this occurs then fuck the world
}
