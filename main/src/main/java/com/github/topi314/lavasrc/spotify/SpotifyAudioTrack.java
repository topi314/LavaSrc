package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public class SpotifyAudioTrack extends MirroringAudioTrack {

	private volatile AudioTrackInfo trackInfoWithIsrc;

	public SpotifyAudioTrack(AudioTrackInfo trackInfo, SpotifySourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public SpotifyAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		var isrc = this.trackInfo.isrc;
		if ((isrc == null || isrc.isEmpty()) && !this.isPreview() && !isLocal()) {
			var fetchedIsrc = ((SpotifySourceManager) this.sourceManager).partnerApiClient.fetchIsrcViaSpClientMetadata(this.trackInfo.identifier);
			if (fetchedIsrc != null && !fetchedIsrc.isEmpty()) {
				this.trackInfoWithIsrc = new AudioTrackInfo(
						this.trackInfo.title,
						this.trackInfo.author,
						this.trackInfo.length,
						this.trackInfo.identifier,
						this.trackInfo.isStream,
						this.trackInfo.uri,
						this.trackInfo.artworkUrl,
						fetchedIsrc);
			}
		}
		super.process(executor);
	}

	@Override
	public AudioTrackInfo getInfo() {
		return this.trackInfoWithIsrc != null ? this.trackInfoWithIsrc : super.getInfo();
	}

	@Override
	protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
		return new Mp3AudioTrack(trackInfo, stream);
	}

	@Override
	protected AudioTrack makeShallowClone() {
		var clone = new SpotifyAudioTrack(this.getInfo(), this.albumName, this.albumUrl, this.artistUrl,
				this.artistArtworkUrl, this.previewUrl, this.isPreview, this.sourceManager);
		if (this.trackInfoWithIsrc != null) {
			clone.trackInfoWithIsrc = this.trackInfoWithIsrc;
		}
		return clone;
	}

	public boolean isLocal() {
		return this.trackInfo.identifier.equals("local");
	}

}
