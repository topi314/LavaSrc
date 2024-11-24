package com.github.topi314.lavasrc;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtendedAudioPlaylist extends BasicAudioPlaylist {

  @NotNull
  protected final Type type;

  @Nullable
  protected final String url;

  @Nullable
  protected final String artworkURL;

  @Nullable
  protected final String author;

  @Nullable
  protected final Integer totalTracks;

  public ExtendedAudioPlaylist(
    String name,
    List<AudioTrack> tracks,
    @NotNull Type type,
    @Nullable String url,
    @Nullable String artworkURL,
    @Nullable String author,
    @Nullable Integer totalTracks
  ) {
    super(name, tracks, null, false);
    this.type = type;
    this.url = url;
    this.artworkURL = artworkURL;
    this.author = author;
    this.totalTracks = totalTracks;
  }

  @NotNull
  public Type getType() {
    return type;
  }

  @Nullable
  public String getUrl() {
    return this.url;
  }

  @Nullable
  public String getArtworkURL() {
    return this.artworkURL;
  }

  @Nullable
  public String getAuthor() {
    return this.author;
  }

  @Nullable
  public Integer getTotalTracks() {
    return this.totalTracks;
  }

  public enum Type {
    ALBUM("album"),
    PLAYLIST("playlist"),
    ARTIST("artist"),
    RECOMMENDATIONS("recommendations");

    public final String name;

    Type(String name) {
      this.name = name;
    }
  }
}
