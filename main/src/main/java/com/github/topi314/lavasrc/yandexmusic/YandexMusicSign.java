package com.github.topi314.lavasrc.yandexmusic;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class YandexMusicSign {
	private static final String ANDROID_SIGN_KEY = "p93jhgh689SBReK6ghtw62";

	public String value;
	public long timestamp;

	public YandexMusicSign(String value, long timestamp) {
		this.value = value;
		this.timestamp = timestamp;
	}

	public static YandexMusicSign create(String trackId) {
		try {
			var timestamp = System.currentTimeMillis() / 1000;
			var message = trackId + timestamp;

			Mac hmac = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKey = new SecretKeySpec(ANDROID_SIGN_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
			hmac.init(secretKey);

			var sign = Base64.getEncoder().encodeToString(hmac.doFinal(message.getBytes(StandardCharsets.UTF_8)));

			return new YandexMusicSign(URLEncoder.encode(sign, StandardCharsets.UTF_8), timestamp);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}
}
