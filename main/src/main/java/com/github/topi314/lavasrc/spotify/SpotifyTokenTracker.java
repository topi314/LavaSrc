package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasrc.LavaSrcTools;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotifyTokenTracker {
	private static final Logger log = LoggerFactory.getLogger(SpotifyTokenTracker.class);

	private final SpotifySourceManager sourceManager;

	private String clientId;
	private String clientSecret;
	private String accessToken;
	private Instant expires;

	private String spDc;
	private String accountToken;
	private Instant accountTokenExpire;

	public SpotifyTokenTracker(SpotifySourceManager source, String clientId, String clientSecret, String spDc) {
		this.sourceManager = source;
		this.clientId = clientId;
		this.clientSecret = clientSecret;

		if (!hasValidCredentials()) {
			log.debug("Missing/invalid credentials, falling back to public token.");
		} else {
			log.debug("Valid credentials found, ready to request access token.");
		}

		this.spDc = spDc;

		if (!hasValidAccountCredentials()) {
			log.debug("Missing/invalid account credentials");
		}
	}

	public void setClientIDS(String clientId, String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.accessToken = null;
		this.expires = null;
	}

	public String getAccessToken() throws IOException {
		if (this.accessToken == null || this.expires == null || this.expires.isBefore(Instant.now())) {
			synchronized (this) {
				if (accessToken == null || this.expires == null || this.expires.isBefore(Instant.now())) {
					log.debug("Access token is invalid or expired, refreshing token...");
					this.refreshAccessToken();
				}
			}
		}
		return this.accessToken;
	}

	private void refreshAccessToken() throws IOException {
		boolean usePublicToken = !hasValidCredentials();
		HttpUriRequest request;

		if (!usePublicToken) {
			request = new HttpPost("https://accounts.spotify.com/api/token");
			request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((this.clientId + ":" + this.clientSecret).getBytes(StandardCharsets.UTF_8)));
			((HttpPost) request).setEntity(new UrlEncodedFormEntity(List.of(new BasicNameValuePair("grant_type", "client_credentials")), StandardCharsets.UTF_8));
		} else {
			request = new HttpGet(generateGetAccessTokenURL());
		}

		try {
			var json = LavaSrcTools.fetchResponseAsJson(sourceManager.getHttpInterface(), request);
			if (!json.get("error").isNull()) {
				String error = json.get("error").text();
				log.error("Error while fetching access token: {}", error);
				throw new RuntimeException("Error while fetching access token: " + error);
			}

			if (!usePublicToken) {
				accessToken = json.get("access_token").text();
				expires = Instant.now().plusSeconds(json.get("expires_in").asLong(0));
			} else {
				accessToken = json.get("accessToken").text();
				expires = Instant.ofEpochMilli(json.get("accessTokenExpirationTimestampMs").asLong(0));
			}
		} catch (IOException e) {
			log.error("Access token refreshing failed.", e);
			throw new RuntimeException("Access token refreshing failed", e);
		}
	}

	private boolean hasValidCredentials() {
		return clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty();
	}

	public void setSpDc(String spDc) {
		this.spDc = spDc;
		this.accountToken = null;
		this.accountTokenExpire = null;
	}

	public String getAccountToken() throws IOException {
		if (this.accountToken == null || this.accountTokenExpire == null || this.accountTokenExpire.isBefore(Instant.now())) {
			synchronized (this) {
				if (this.accountToken == null || this.accountTokenExpire == null || this.accountTokenExpire.isBefore(Instant.now())) {
					log.debug("Account token is invalid or expired, refreshing token...");
					this.refreshAccountToken();
				}
			}
		}
		return this.accountToken;
	}

	public void refreshAccountToken() throws IOException {
		var request = new HttpGet(generateGetAccessTokenURL());
		request.addHeader("App-Platform", "WebPlayer");
		request.addHeader("Cookie", "sp_dc=" + this.spDc);

		try {
			var json = LavaSrcTools.fetchResponseAsJson(this.sourceManager.getHttpInterface(), request);
			if (!json.get("error").isNull()) {
				String error = json.get("error").text();
				log.error("Error while fetching account token: {}", error);
				throw new RuntimeException("Error while fetching account token: " + error);
			}
			this.accountToken = json.get("accessToken").text();
			this.accountTokenExpire = Instant.ofEpochMilli(json.get("accessTokenExpirationTimestampMs").asLong(0));
		} catch (IOException e) {
			log.error("Account token refreshing failed.", e);
			throw new RuntimeException("Account token refreshing failed", e);
		}
	}

	public boolean hasValidAccountCredentials() {
		return this.spDc != null && !this.spDc.isEmpty();
	}

	public static String generateTOTP(String secret, int period, int digits) {
		long time = System.currentTimeMillis() / 1000 / period;
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putLong(time);
		byte[] timeBytes = buffer.array();

		try {
			SecretKeySpec keySpec = new SecretKeySpec(hexStringToByteArray(secret), "HmacSHA1");
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(keySpec);
			byte[] hash = mac.doFinal(timeBytes);
			int offset = hash[hash.length - 1] & 0xF;
			int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16) | // please no break
				((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
			int otp = binary % (int) Math.pow(10, digits);
			return String.format("%0" + digits + "d", otp);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException("Error generating TOTP", e);
		}
	}

	public static byte[] extractSecret(CloseableHttpClient client, String scriptUrl) throws IOException {
		HttpGet scriptRequest = new HttpGet(scriptUrl);
		try (CloseableHttpResponse scriptResponse = client.execute(scriptRequest)) {
			String scriptContent = EntityUtils.toString(scriptResponse.getEntity());

			Pattern pattern = Pattern.compile("secret:function\\([^)]+\\)\\{.*?\\[(.*?)\\].*?\\}", Pattern.DOTALL); // fuck spotify
			Matcher matcher = pattern.matcher(scriptContent);
			if (matcher.find()) {
				String secretArrayString = matcher.group(1);
				String[] secretArray = secretArrayString.split(","); // gay asf that i did it like this but fuck it
				byte[] secretByteArray = new byte[secretArray.length];
				for (int i = 0; i < secretArray.length; i++) {
					secretByteArray[i] = (byte) Integer.parseInt(secretArray[i].trim());
				}

				return secretByteArray;
			} else {
				log.error("No secret array found in script: {}", scriptUrl);
				return null;
			}
		}
	}

	public static byte[] requestSecret() throws IOException {
		String homepageUrl = "https://open.spotify.com/";
		String scriptPattern = "mobile-web-player";

		log.debug("Requesting secret from Spotify homepage: {}", homepageUrl);

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpGet request = new HttpGet(homepageUrl);
			try (CloseableHttpResponse response = client.execute(request)) {
				String html = EntityUtils.toString(response.getEntity());
				Document doc = Jsoup.parse(html);
				Elements scriptElements = doc.select("script[src]");
				List<String> scriptUrls = new ArrayList<>();
				log.debug("Found {} script elements in the HTML", scriptElements.size());
				for (Element script : scriptElements) {
					String scriptUrl = script.attr("src");
					if (scriptUrl.contains(scriptPattern) && !scriptUrl.contains("vendor")) {
						scriptUrls.add(scriptUrl);
						log.debug("Found relevant script URL: {}", scriptUrl);
					}
				}
				if (scriptUrls.isEmpty()) {
					log.debug("No relevant script URLs found.");
					return null;
				}
				for (String scriptUrl : scriptUrls) {
					log.debug("Attempting to extract secret from script URL: {}", scriptUrl);
					byte[] secret = extractSecret(client, scriptUrl);
					if (secret != null) {
						log.debug("Successfully extracted secret.");
						return secret;
					}
				}
			}
		} catch (IOException e) {
			log.error("Failed to request or parse the secret", e);
			throw new IOException("Failed to request or parse the secret", e);
		}
		log.error("No secret found.");
		return null;
	}

	public static String generateGetAccessTokenURL() throws IOException {
		var secret = requestSecret();
		if (secret == null) {
			throw new IOException("Failed to retrieve secret from Spotify.");
		}
		byte[] transformedSecret = convertArrayToTransformedByteArray(secret);
		var hexSecret = toHexString(transformedSecret);
		var totp = generateTOTP(hexSecret, 30, 6);
		long ts = System.currentTimeMillis();
		return "https://open.spotify.com/get_access_token?reason=transport&productType=web-player&totp="
			+ totp + "&totpVer=5&ts=" + ts;
	}

	public static byte[] convertArrayToTransformedByteArray(byte[] array) {
		byte[] transformed = new byte[array.length];
		for (int i = 0; i < array.length; i++) {
			// XOR with dat transform
			transformed[i] = (byte) (array[i] ^ ((i % 33) + 9));
		}
		return transformed;
	}

	public static String toHexString(byte[] transformed) {
		StringBuilder joinedString = new StringBuilder();
		for (byte b : transformed) {
			joinedString.append(b);
		}
		byte[] utf8Bytes = joinedString.toString().getBytes(StandardCharsets.UTF_8);
		StringBuilder hexString = new StringBuilder();
		for (byte b : utf8Bytes) {
			hexString.append(String.format("%02x", b));
		}
		return hexString.toString();
	}

	private static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
				+ Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}
}
