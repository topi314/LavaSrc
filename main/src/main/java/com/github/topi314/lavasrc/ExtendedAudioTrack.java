package com.github.topi314.lavasrc;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import org.jetbrains.annotations.Nullable;

public abstract class ExtendedAudioTrack extends DelegatedAudioTrack {

  @Nullable
  protected final String albumName;

  @Nullable
  protected final String albumUrl;

  @Nullable
  protected final String artistUrl;

  @Nullable
  protected final String artistArtworkUrl;

  @Nullable
  protected final String previewUrl;

  protected final boolean isPreview;

  public ExtendedAudioTrack(
    AudioTrackInfo trackInfo,
    @Nullable String albumName,
    @Nullable String albumUrl,
    @Nullable String artistUrl,
    @Nullable String artistArtworkUrl,
    @Nullable String previewUrl,
    boolean isPreview
  ) {
    super(trackInfo);
    this.albumName = albumName;
    this.albumUrl = albumUrl;
    this.artistUrl = artistUrl;
    this.artistArtworkUrl = artistArtworkUrl;
    this.previewUrl = previewUrl;
    this.isPreview = isPreview;
  }

  @Nullable
  public String getAlbumName() {
    return this.albumName;
  }

  @Nullable
  public String getAlbumUrl() {
    return albumUrl;
  }

  @Nullable
  public String getArtistUrl() {
    return artistUrl;
  }

  @Nullable
  public String getArtistArtworkUrl() {
    return this.artistArtworkUrl;
  }

  @Nullable
  public String getPreviewUrl() {
    return this.previewUrl;
  }

  public boolean isPreview() {
    return this.isPreview;
  }
}
