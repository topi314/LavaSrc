package com.github.topi314.lavasrc.yandexmusic;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.List;

public class YandexMusicAudioPlaylist extends ExtendedAudioPlaylist {

  public YandexMusicAudioPlaylist(
    String name,
    List<AudioTrack> tracks,
    ExtendedAudioPlaylist.Type type,
    String identifier,
    String artworkURL,
    String author
  ) {
    super(name, tracks, type, identifier, artworkURL, author, null);
  }
}
