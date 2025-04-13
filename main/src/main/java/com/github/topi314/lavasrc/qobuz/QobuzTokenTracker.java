package com.github.topi314.lavasrc.qobuz;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.regex.Pattern;

public class QobuzTokenTracker {

	private static final Logger log = LoggerFactory.getLogger(QobuzTokenTracker.class);
	private static final Pattern BUNDLE_PATTERN = Pattern.compile("<script src=\"(?<bundleJS>/resources/\\d+\\.\\d+\\.\\d+-[a-z]\\d{3}/bundle\\.js)\"");
	private static final Pattern APP_ID_PATTERN = Pattern.compile("production:\\{api:\\{appId:\"(?<appID>.*?)\",appSecret:");
	private static final Pattern SEED_PATTERN = Pattern.compile("\\):[a-z]\\.initialSeed\\(\"(?<seed>.*?)\",window\\.utimezone\\.(?<timezone>[a-z]+)\\)");
	private static final String WEB_PLAYER_BASE_URL = "https://play.qobuz.com";

	private final QobuzAudioSourceManager sourceManager;

	private String appId;
	private String appSecret;
	private String userOauthToken;


	public QobuzTokenTracker(QobuzAudioSourceManager sourceManager, String userOauthToken, String appId, String appSecret) {
		this.sourceManager = sourceManager;

		if (userOauthToken == null || userOauthToken.isEmpty()) {
			throw new IllegalArgumentException("User Oauth token cannot be null or empty.");
		}
		this.userOauthToken = userOauthToken;

		if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
			this.fetchAppInfo();
		} else {
			this.appId = appId;
			this.appSecret = appSecret;
		}
	}

	private static String capitalize(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
	}

	public String getAppSecret() {
		return this.appSecret;
	}

	public void setAppSecret(String appSecret) {
		this.appSecret = appSecret;
	}

	public String getUserOauthToken() {
		return this.userOauthToken;
	}

	public void setUserOauthToken(String userOauthToken) {
		this.userOauthToken = userOauthToken;
	}

	public String getAppId() {
		return this.appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String fetchBundleString() throws IOException {
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			var request = new HttpGet(WEB_PLAYER_BASE_URL + "/login");
			String bundleUrl;
			try (var response = httpInterface.execute(request)) {
				var bundleMatcher = BUNDLE_PATTERN.matcher(EntityUtils.toString(response.getEntity()));
				if (!bundleMatcher.find()) {
					throw new IllegalStateException("Failed to extract bundle.js URL");
				}
				bundleUrl = WEB_PLAYER_BASE_URL + bundleMatcher.group("bundleJS");
			}
			var bundleRequest = new HttpGet(bundleUrl);
			try (var response = httpInterface.execute(bundleRequest)) {
				return EntityUtils.toString(response.getEntity());
			}
		}
	}

	public String getWebPlayerAppId(String bundleJsContent) {
		var appIdMatcher = APP_ID_PATTERN.matcher(bundleJsContent);
		if (!appIdMatcher.find()) {
			throw new IllegalStateException("Failed to extract app_id from bundle.js");
		}
		return appIdMatcher.group("appID");
	}

	public String getWebPlayerAppSecret(String bundleJsContent) {
		var seedMatcher = SEED_PATTERN.matcher(bundleJsContent);
		if (!seedMatcher.find()) {
			throw new IllegalStateException("Failed to extract seed and timezone from bundle.js");
		}

		var seed = seedMatcher.group("seed");
		var productionTimezone = capitalize(seedMatcher.group("timezone"));
		var infoExtrasRegex = Pattern.compile("timezones:\\[.*?name:.*?/" + productionTimezone + "\",info:\"(?<info>.*?)\",extras:\"(?<extras>.*?)\"");
		var infoExtrasMatcher = infoExtrasRegex.matcher(bundleJsContent);
		if (!infoExtrasMatcher.find()) {
			throw new IllegalStateException("Failed to extract info and extras for timezone " + productionTimezone + " from bundle.js");
		}
		var base64EncodedAppSecret = seed + infoExtrasMatcher.group("info") + infoExtrasMatcher.group("extras");
		base64EncodedAppSecret = base64EncodedAppSecret.substring(0, base64EncodedAppSecret.length() - 44);
		return new String(Base64.getDecoder().decode(base64EncodedAppSecret));
	}

	private void fetchAppInfo() {
		try {
			var bundleJsContent = this.fetchBundleString();
			this.appId = this.getWebPlayerAppId(bundleJsContent);
			this.appSecret = this.getWebPlayerAppSecret(bundleJsContent);
			log.info("Fetched Qobuz App ID :{} and App Secret :{}", this.appId, this.appSecret);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to extract app_id from bundle.js", e);
		}
	}

}
