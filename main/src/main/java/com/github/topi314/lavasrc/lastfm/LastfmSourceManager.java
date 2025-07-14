package com.github.topi314.lavasrc.lastfm;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrackResolver;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;


public class LastfmSourceManager extends MirroringAudioSourceManager implements HttpConfigurable {

	public static final Pattern URL_PATTERN = Pattern.compile("(https?://)(www\\.)?last\\.fm/music/(?<artist>[^/]+)(?:/(?<album>[^/]+))?(?:/_/(?<track>[^/]+))?");
	public static final String SEARCH_PREFIX = "lfsearch:";
	public static final String API_BASE = "https://ws.audioscrobbler.com/2.0/";

	private static final Logger log = LoggerFactory.getLogger(LastfmSourceManager.class);

	private final HttpInterfaceManager httpInterfaceManager;
	private final String apiKey;
	private int playlistPageLimit = 6;


	public LastfmSourceManager(String apiKey, String[] providers, AudioPlayerManager audioPlayerManager) {
		this(apiKey, unused -> audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public LastfmSourceManager(String apiKey, String[] providers, Function<Void, AudioPlayerManager> audioPlayerManager) {
		this(apiKey, audioPlayerManager, new DefaultMirroringAudioTrackResolver(providers));
	}

	public LastfmSourceManager(String apiKey, Function<Void, AudioPlayerManager> audioPlayerManager, MirroringAudioTrackResolver mirroringAudioTrackResolver) {
		super(audioPlayerManager, mirroringAudioTrackResolver);
		this.apiKey = apiKey;
		this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	}

	@NotNull
	@Override
	public String getSourceName() {
		return "lastfm";
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		try {
			if (reference.identifier.startsWith(SEARCH_PREFIX)) {
				return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()));
			}

			var matcher = URL_PATTERN.matcher(reference.identifier);
			if (!matcher.find()) {
				return null;
			}

			var artist = matcher.group("artist");
			var album = matcher.group("album");
			var track = matcher.group("track");

			if (track != null) {
				return this.getTrack(artist, track);
			}
			if (album != null) {
				return this.getAlbum(artist, album);
			}
			return this.getArtist(artist);

		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, java.io.DataOutput output) throws IOException {
		// No custom values to encode
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		return new LastfmAudioTrack(trackInfo, this);
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

	private JsonBrowser getJson(URIBuilder builder) throws IOException, URISyntaxException {
		builder.addParameter("api_key", this.apiKey);
		builder.addParameter("format", "json");
		// Convert URI to HttpGet request
		HttpGet request = new HttpGet(builder.build());
		return LavaSrcTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), request);
	}

	private AudioItem getSearch(String query) throws IOException, URISyntaxException {
		var builder = new URIBuilder(API_BASE)
			.addParameter("method", "track.search")
			.addParameter("track", query);
		var json = this.getJson(builder);
		if (json == null || json.get("results").get("trackmatches").get("track").values().isEmpty()) {
			return AudioReference.NO_TRACK;
		}
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.get("results").get("trackmatches").get("track").values()) {
			tracks.add(this.buildTrack(track));
		}
		return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
	}

	private AudioItem getTrack(String artist, String track) throws IOException, URISyntaxException {
		var builder = new URIBuilder(API_BASE)
			.addParameter("method", "track.getInfo")
			.addParameter("artist", artist)
			.addParameter("track", track);
		var json = this.getJson(builder);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		return this.buildTrack(json.get("track"));
	}

	private AudioItem getAlbum(String artist, String album) throws IOException, URISyntaxException {
		var builder = new URIBuilder(API_BASE)
			.addParameter("method", "album.getInfo")
			.addParameter("artist", artist)
			.addParameter("album", album);
		var json = this.getJson(builder);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.get("album").get("tracks").get("track").values()) {
			tracks.add(this.buildTrack(track));
		}
		var albumJson = json.get("album");
		return new LastfmAudioPlaylist(albumJson.get("name").text(), tracks, ExtendedAudioPlaylist.Type.ALBUM, albumJson.get("url").text(), albumJson.get("image").index(3).get("#text").text(), albumJson.get("artist").text(), tracks.size());
	}

	private AudioItem getArtist(String artist) throws IOException, URISyntaxException {
		var builder = new URIBuilder(API_BASE)
			.addParameter("method", "artist.gettoptracks")
			.addParameter("artist", artist);
		var json = this.getJson(builder);
		if (json == null) {
			return AudioReference.NO_TRACK;
		}
		var tracks = new ArrayList<AudioTrack>();
		for (var track : json.get("toptracks").get("track").values()) {
			tracks.add(this.buildTrack(track));
		}

		var artistJson = json.get("toptracks").get("@attr");
		var artistName = artistJson.get("artist").text();
		return new LastfmAudioPlaylist(artistName + "'s Top Tracks", tracks, ExtendedAudioPlaylist.Type.ARTIST, "https://www.last.fm/music/" + artistName, null, artistName, tracks.size());
	}

	private AudioTrack buildTrack(JsonBrowser track) {
		var artist = track.get("artist");
		var artistName = artist.get("name").text();
		if (artistName == null) {
			artistName = artist.text();
		}
		var artworkUrl = track.get("image").index(3).get("#text").text();
		return new LastfmAudioTrack(
			new AudioTrackInfo(
				track.get("name").text(),
				artistName,
				track.get("duration").asLong(0) * 1000,
				track.get("url").text(),
				false,
				track.get("url").text(),
				artworkUrl,
				null
			), this);
	}
}
