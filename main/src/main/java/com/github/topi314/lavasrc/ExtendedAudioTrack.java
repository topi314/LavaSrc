package com.github.topi314.lavasrc;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;

public abstract class ExtendedAudioTrack extends DelegatedAudioTrack {

	protected final String albumName;
	protected final String artistArtworkUrl;
	protected final String previewUrl;

	public ExtendedAudioTrack(AudioTrackInfo trackInfo, String albumName, String artistArtworkUrl, String previewUrl) {
		super(trackInfo);
		this.albumName = albumName;
		this.artistArtworkUrl = artistArtworkUrl;
		this.previewUrl = previewUrl;
	}

	public String getAlbumName() {
		return this.albumName;
	}

	public String getArtistArtworkUrl() {
		return this.artistArtworkUrl;
	}

	public String getPreviewUrl() {
		return this.previewUrl;
	}

}
