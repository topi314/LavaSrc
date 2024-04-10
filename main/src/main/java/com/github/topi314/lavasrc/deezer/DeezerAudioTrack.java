package com.github.topi314.lavasrc.deezer;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.container.flac.FlacAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class DeezerAudioTrack extends ExtendedAudioTrack {

	private final DeezerAudioSourceManager sourceManager;
	private final CookieStore cookieStore;

	public DeezerAudioTrack(AudioTrackInfo trackInfo, DeezerAudioSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public DeezerAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, DeezerAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
		this.sourceManager = sourceManager;
		this.cookieStore = new BasicCookieStore();
	}

	private JsonBrowser getJsonResponse(HttpUriRequest request, boolean includeArl) {
		try (HttpInterface httpInterface = this.sourceManager.getHttpInterface()) {
			httpInterface.getContext().setRequestConfig(RequestConfig.custom().setCookieSpec("standard").build());
			httpInterface.getContext().setCookieStore(cookieStore);

			if (includeArl && this.sourceManager.getArl() != null) {
				request.setHeader("Cookie", "arl=" + this.sourceManager.getArl());
			}

			return LavaSrcTools.fetchResponseAsJson(httpInterface, request);
		} catch (IOException e) {
			throw ExceptionTools.toRuntimeException(e);
		}
	}

	private String getSessionId() {
		final HttpPost getSessionID = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.ping&input=3&api_version=1.0&api_token=");
		final JsonBrowser sessionIdJson = this.getJsonResponse(getSessionID, false);

		this.checkResponse(sessionIdJson, "Failed to get session ID: ");
		if (sessionIdJson.get("data").index(0).get("errors").index(0).get("code").asLong(0) != 0) {
			throw new IllegalStateException("Failed to get session ID");
		}

		return sessionIdJson.get("results").get("SESSION").text();
	}

	private JsonBrowser generateLicenceToken(boolean useArl) {
		final HttpGet request = new HttpGet(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=");

		// session ID is not needed with ARL and vice-versa.
		if (!useArl || this.sourceManager.getArl() == null) {
			request.setHeader("Cookie", "sid=" + this.getSessionId());
		}

		return this.getJsonResponse(request, useArl);
	}

	private SourceWithFormat getSource(boolean tryFlac, boolean isRetry) throws URISyntaxException {
		var json = this.generateLicenceToken(tryFlac);
		this.checkResponse(json, "Failed to get user token: ");

		var userLicenseToken = json.get("results").get("USER").get("OPTIONS").get("license_token").text();
		var apiToken = json.get("results").get("checkForm").text();

		var getTrackToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=song.getData&input=3&api_version=1.0&api_token=" + apiToken);
		getTrackToken.setEntity(new StringEntity("{\"sng_id\":\"" + this.trackInfo.identifier + "\"}", ContentType.APPLICATION_JSON));
		var trackTokenJson = this.getJsonResponse(getTrackToken, tryFlac);

		this.checkResponse(trackTokenJson, "Failed to get track token: ");

		if (trackTokenJson.get("error").get("VALID_TOKEN_REQUIRED").text() != null && !isRetry) {
			// "error":{"VALID_TOKEN_REQUIRED":"Invalid CSRF token"}
			// seems to indicate an invalid API token?
			return this.getSource(tryFlac, true);
		}

		if (tryFlac && trackTokenJson.get("results").get("FILESIZE_FLAC").asLong(0) == 0) {
			// no flac format available.
			return this.getSource(false, false);
		}

		var trackToken = trackTokenJson.get("results").get("TRACK_TOKEN").text();
		var getMediaURL = new HttpPost(DeezerAudioSourceManager.MEDIA_BASE + "/get_url");

		getMediaURL.setEntity(new StringEntity("{\"license_token\":\"" + userLicenseToken + "\",\"media\":[{\"type\":\"FULL\",\"formats\":[{\"cipher\":\"BF_CBC_STRIPE\",\"format\":\"MP3_128\"}]}],\"track_tokens\": [\"" + trackToken + "\"]}", ContentType.APPLICATION_JSON));
		json = this.getJsonResponse(getMediaURL, tryFlac);

		try {
			this.checkResponse(json, "Failed to get media URL: ");
		} catch (IllegalStateException e) {
			// error code 2000 = failed to decode track token
			if (e.getMessage().contains("2000:") && !isRetry) {
				return this.getSource(tryFlac, true);
			} else if (tryFlac) {
				cookieStore.clear();
				return this.getSource(false, false); // Try again but for MP3_128.
			} else {
				throw e;
			}
		}

		return SourceWithFormat.fromResponse(json, trackTokenJson);
	}

	private void checkResponse(JsonBrowser json, String message) throws IllegalStateException {
		if (json == null) {
			throw new IllegalStateException(message + "No response");
		}

		var errors = json.get("data").index(0).get("errors").values();

		if (!errors.isEmpty()) {
			var errorsStr = errors.stream().map(error -> error.get("code").text() + ": " + error.get("message").text()).collect(Collectors.joining(", "));
			throw new IllegalStateException(message + errorsStr);
		}
	}

	private byte[] getTrackDecryptionKey() throws NoSuchAlgorithmException {
		var md5 = Hex.encodeHex(MessageDigest.getInstance("MD5").digest(this.trackInfo.identifier.getBytes()), true);
		var master_key = this.sourceManager.getMasterDecryptionKey().getBytes();

		var key = new byte[16];
		for (int i = 0; i < 16; i++) {
			key[i] = (byte) (md5[i] ^ md5[i + 16] ^ master_key[i]);
		}
		return key;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			if (this.isPreview) {
				if (this.previewUrl == null) {
					throw new FriendlyException("No preview url found", FriendlyException.Severity.COMMON, new IllegalArgumentException());
				}

				try (var stream = new PersistentHttpStream(httpInterface, new URI(this.previewUrl), this.trackInfo.length)) {
					processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
				}
			} else {
				SourceWithFormat source = this.getSource(this.sourceManager.getArl() != null, false);

				try (var stream = new DeezerPersistentHttpStream(httpInterface, source.url, source.contentLength, this.getTrackDecryptionKey())) {
					processDelegate(source.getTrackFactory().apply(this.trackInfo, stream), executor);
				}
			}
		}
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new DeezerAudioTrack(this.trackInfo, this.albumName, this.albumUrl, this.artistUrl, this.artistArtworkUrl, this.previewUrl, this.isPreview, this.sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

	private static class SourceWithFormat {
		private final URI url;
		private final String format;
		private final long contentLength;

		private SourceWithFormat(String url, String format, long contentLength) throws URISyntaxException {
			this.url = new URI(url);
			this.format = format;
			this.contentLength = contentLength;
		}

		private BiFunction<AudioTrackInfo, PersistentHttpStream, InternalAudioTrack> getTrackFactory() {
			return this.format.equals("FLAC") ? FlacAudioTrack::new : Mp3AudioTrack::new;
		}

		private static SourceWithFormat fromResponse(JsonBrowser json, JsonBrowser trackJson) throws URISyntaxException {
			JsonBrowser media = json.get("data").index(0).get("media").index(0);
			JsonBrowser sources = media.get("sources");

			if (media.isNull()) {
				return null;
			}

			String format = media.get("format").text();
			String url = sources.index(0).get("url").text();
			long contentLength = trackJson.get("results").get("FILESIZE_" + format).asLong(Units.CONTENT_LENGTH_UNKNOWN);
			return new SourceWithFormat(url, format, contentLength);
		}
	}
}
