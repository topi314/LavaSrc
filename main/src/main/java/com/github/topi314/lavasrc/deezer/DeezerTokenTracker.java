package com.github.topi314.lavasrc.deezer;

import com.github.topi314.lavasrc.LavaSrcTools;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class DeezerTokenTracker {

	private final DeezerAudioSourceManager sourceManager;


	private String arl;

	private Tokens tokens;


	public DeezerTokenTracker(DeezerAudioSourceManager sourceManager, String arl) {
		this.sourceManager = sourceManager;
		if (arl == null || arl.isEmpty()) {
			throw new NullPointerException("Deezer arl must be set");
		}
		this.arl = arl;
	}

	public String getArl() {
		return this.arl;
	}

	public void setArl(String arl) {
		if (arl == null || arl.isEmpty()) {
			throw new NullPointerException("Deezer arl must be set");
		}
		this.arl = arl;
	}

	private void refreshSession() throws IOException {
		try (var httpInterface = sourceManager.getHttpInterface()) {
			var cookieStore = new BasicCookieStore();
			httpInterface.getContext().setCookieStore(cookieStore);
			httpInterface.getContext().setRequestConfig(
				RequestConfig.copy(httpInterface.getContext().getRequestConfig())
					.setCookieSpec(CookieSpecs.STANDARD)
					.build()
			);

			var getUserToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=");
			var json = LavaSrcTools.fetchResponseAsJson(httpInterface, getUserToken);
			DeezerAudioSourceManager.checkResponse(json, "Failed to get user token");

			String sessionID = null;
			String dzrUniqId = null;
			for (var cookie : cookieStore.getCookies()) {
				switch (cookie.getName()) {
					case "sid":
						sessionID = cookie.getValue();
						break;
					case "dzr_uniq_id":
						dzrUniqId = cookie.getValue();
						break;
				}
			}

			if (sessionID == null) {
				throw new IOException("Failed to find sid cookie");
			}
			if (dzrUniqId == null) {
				throw new IOException("Failed to find dzr uniq id cookie");
			}

			this.tokens = new Tokens(
				sessionID,
				dzrUniqId,
				json.get("results").get("checkForm").text(),
				json.get("results").get("USER").get("OPTIONS").get("license_token").text(),
				Instant.now().plus(3600, ChronoUnit.SECONDS)
			);
		}
	}

	public Tokens getTokens() throws IOException {
		if (this.tokens == null || Instant.now().isAfter(this.tokens.expireAt)) {
			this.refreshSession();
		}
		return this.tokens;
	}

	public static class Tokens {
		public String sessionId;
		public String dzrUniqId;
		public String api;
		public String license;
		public Instant expireAt;

		public Tokens(String sessionId, String dzrUniqId, String api, String license, Instant expireAt) {
			this.sessionId = sessionId;
			this.dzrUniqId = dzrUniqId;
			this.api = api;
			this.license = license;
			this.expireAt = expireAt;
		}
	}
}
