package com.github.topi314.lavasrc.extended;

import org.jetbrains.annotations.Nullable;

public class ExtendedAudioTrackInfo {

	@Nullable
	public final String albumName;
	@Nullable
	public final String albumUrl;
	@Nullable
	public final String artistUrl;
	@Nullable
	public final String artistArtworkUrl;
	public final boolean isPreview;

	public ExtendedAudioTrackInfo(@Nullable String albumName, @Nullable String albumUrl, @Nullable String artistUrl, @Nullable String artistArtworkUrl, boolean isPreview) {
		this.albumName = albumName;
		this.albumUrl = albumUrl;
		this.artistUrl = artistUrl;
		this.artistArtworkUrl = artistArtworkUrl;
		this.isPreview = isPreview;
	}

}
