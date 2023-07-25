package com.github.topi314.lavasrc;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;

public abstract class ExtendedAudioTrack extends DelegatedAudioTrack {

	protected final String albumName;
	protected final String artistArtworkUrl;
	protected final String previewUrl;
	protected final String artistUrl;
	protected final boolean isPreview;

	public ExtendedAudioTrack(AudioTrackInfo trackInfo, String albumName, String artistArtworkUrl, String previewUrl, String artistUrl, boolean isPreview) {
		super(trackInfo);
		this.albumName = albumName;
		this.artistArtworkUrl = artistArtworkUrl;
		this.previewUrl = previewUrl;
		this.artistUrl = artistUrl;
		this.isPreview = isPreview;
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

	public String getArtistUrl() {
		return artistUrl;
	}

	public boolean isPreview() {
		return this.isPreview;
	}

}
