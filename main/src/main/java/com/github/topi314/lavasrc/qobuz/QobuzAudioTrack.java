package com.github.topi314.lavasrc.qobuz;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.security.MessageDigest;

public class QobuzAudioTrack extends ExtendedAudioTrack {
	private final QobuzAudioSourceManager sourceManager;

	public QobuzAudioTrack(AudioTrackInfo trackInfo, QobuzAudioSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, sourceManager);
	}

	public QobuzAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, QobuzAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, null, false);
		this.sourceManager = sourceManager;
	}

	private static String getMd5Hash(String input) throws Exception {
		var md = MessageDigest.getInstance("MD5");
		var messageDigest = md.digest(input.getBytes());
		var hexString = new StringBuilder();
		for (byte b : messageDigest) {
			var hex = Integer.toHexString(0xFF & b);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	private URI getTrackMediaURI() throws Exception {
		var unixTs = System.currentTimeMillis() / 1000L;
		var rSig = String.format("trackgetFileUrlformat_id%dintentstream" + "track_id%d%d%s", 5, Integer.parseInt(this.getIdentifier()), unixTs, this.sourceManager.getAppSecret());
		var rSigHashed = getMd5Hash(rSig);

		var builder = new URIBuilder("https://www.qobuz.com/api.json/0.2/track/getFileUrl");
		builder.addParameter("request_ts", String.valueOf(unixTs));
		builder.addParameter("request_sig", rSigHashed);
		builder.addParameter("track_id", this.getIdentifier());
		builder.addParameter("format_id", "5");
		builder.addParameter("intent", "stream");

		var json = this.sourceManager.getJson(builder.toString());
		if (json == null || json.get("url").isNull()) {
			throw new IllegalStateException("Failed to get track media URI");
		}
		if (!json.get("sample").isNull() && json.get("sample").asBoolean(true)) {
			throw new IllegalStateException("Premium account required to play the whole track");
		}

		return new URI(json.get("url").text());
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		try (var httpInterface = this.sourceManager.getHttpInterface()) {
			try (var stream = new PersistentHttpStream(httpInterface, this.getTrackMediaURI(), null)) {
				processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
			}
		}
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new QobuzAudioTrack(this.trackInfo, this.albumName, this.albumUrl, this.artistUrl, this.artistArtworkUrl, this.sourceManager);
	}

	@Override
	public AudioSourceManager getSourceManager() {
		return this.sourceManager;
	}

}