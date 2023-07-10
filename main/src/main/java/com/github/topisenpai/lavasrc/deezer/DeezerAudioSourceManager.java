package com.github.topisenpai.lavasrc.deezer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class DeezerAudioSourceManager implements AudioSourceManager, HttpConfigurable {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?deezer\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>track|album|playlist|artist)/(?<identifier>[0-9]+)");
	public static final String SEARCH_PREFIX = "dzsearch:";
	public static final String ISRC_PREFIX = "dzisrc:";
	public static final String SHARE_URL = "https://deezer.page.link/";
	public static final String PUBLIC_API_BASE = "https://api.deezer.com/2.0";
	public static final String PRIVATE_API_BASE = "https://www.deezer.com/ajax/gw-light.php";
	public static final String MEDIA_BASE = "https://media.deezer.com/v1";

	private static final Logger log = LoggerFactory.getLogger(DeezerAudioSourceManager.class);

	private final String masterDecryptionKey;

	private final HttpInterfaceManager httpInterfaceManager;

	public DeezerAudioSourceManager(String masterDecryptionKey) {
		if (masterDecryptionKey == null || masterDecryptionKey.isEmpty()) {
			throw new IllegalArgumentException("Deezer master key must be set");
		}
		this.masterDecryptionKey = masterDecryptionKey;
		this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	}

	@Override
	public String getSourceName() {
		return "deezer";
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		try {
			if (reference.identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
			}

			if (reference.identifier.startsWith(ISRC_PREFIX)) {
				return this.getTrackByISRC(reference.identifier.substring(ISRC_PREFIX.length()));
			}

			// If the identifier is a share URL, we need to follow the redirect to find out the real url behind it
			if (reference.identifier.startsWith(SHARE_URL)) {
				var request = new HttpGet(reference.identifier);
				request.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
				try (var response = this.httpInterfaceManager.getInterface().execute(request)) {
					if (response.getStatusLine().getStatusCode() == 302) {
						var location = response.getFirstHeader("Location").getValue();
						if (location.startsWith("https://www.deezer.com/")) {
							return this.loadItem(manager, new AudioReference(location, reference.title));
						}
					}
					return null;
				}
			}

			var matcher = URL_PATTERN.matcher(reference.identifier);
			if (!matcher.find()) {
				return null;
			}

			var id = matcher.group("identifier");
			switch (matcher.group("type")) {
				case "album":
					return this.getAlbum(id);

				case "track":
					return this.getTrack(id);

				case "playlist":
					return this.getPlaylist(id);

				case "artist":
					return this.getArtist(id);
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	public JsonBrowser getJson(String uri) throws IOException {
		var request = new HttpGet(uri);
		request.setHeader("Accept", "application/json");
		return HttpClientTools.fetchResponseAsJson(this.httpInterfaceManager.getInterface(), request);
	}

	private List<AudioTrack> parseTracks(JsonBrowser json) {
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.get("data").values()) {
			if (!track.get("type").text().equals("track")) {
				continue;
			}
			if (!track.get("readable").as(Boolean.class)) {
				log.warn("Skipping track {} by {} because it is not readable. Available countries: {}", track.get("title").text(), track.get("artist").get("name").text(), track.get("available_countries").text());
				continue;
			}
			tracks.add(this.parseTrack(track));
		}
		return tracks;
	}

	private AudioTrack parseTrack(JsonBrowser json) {
		if (!json.get("readable").as(Boolean.class)) {
			throw new FriendlyException("This track is not readable. Available countries: " + json.get("available_countries").text(), FriendlyException.Severity.COMMON, null);
		}
		var id = json.get("id").text();
		return new DeezerAudioTrack(new AudioTrackInfo(
				json.get("title").text(),
				json.get("artist").get("name").text(),
				json.get("duration").as(Long.class) * 1000,
				id,
				false,
				"https://deezer.com/track/" + id),
				json.get("isrc").text(),
				json.get("album").get("cover_xl").text(),
				this
		);
	}

	private AudioItem getTrackByISRC(String isrc) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/track/isrc:" + isrc);
		if (json == null || json.get("id").isNull()) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json);
	}

	private AudioItem getSearch(String query) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
		if (json == null || json.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}

		var tracks = this.parseTracks(json);
		return new BasicAudioPlaylist("Deezer Search: " + query, tracks, null, true);
	}

	private AudioItem getAlbum(String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/album/" + id);
		if (json == null || json.get("tracks").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new BasicAudioPlaylist(json.get("title").text(), this.parseTracks(json.get("tracks")), null, false);
	}

	private AudioItem getTrack(String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/track/" + id);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		return this.parseTrack(json);
	}

	private AudioItem getPlaylist(String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/playlist/" + id);
		if (json == null || json.get("tracks").get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new BasicAudioPlaylist(json.get("title").text(), this.parseTracks(json.get("tracks")), null, false);
	}

	private AudioItem getArtist(String id) throws IOException {
		var json = this.getJson(PUBLIC_API_BASE + "/artist/" + id + "/top?limit=50");
		if (json == null || json.get("data").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		return new BasicAudioPlaylist(json.get("data").index(0).get("artist").get("name").text() + "'s Top Tracks", this.parseTracks(json), null, false);
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
		var deezerAudioTrack = ((DeezerAudioTrack) track);
		DataFormatTools.writeNullableText(output, deezerAudioTrack.getISRC());
		DataFormatTools.writeNullableText(output, deezerAudioTrack.getArtworkURL());
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		return new DeezerAudioTrack(trackInfo,
				DataFormatTools.readNullableText(input),
				DataFormatTools.readNullableText(input),
				this
		);
	}

	@Override
	public void shutdown() {
		try {
			this.httpInterfaceManager.close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		this.httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		this.httpInterfaceManager.configureBuilder(configurator);
	}

	public String getMasterDecryptionKey() {
		return this.masterDecryptionKey;
	}

	public HttpInterface getHttpInterface() {
		return this.httpInterfaceManager.getInterface();
	}

}
