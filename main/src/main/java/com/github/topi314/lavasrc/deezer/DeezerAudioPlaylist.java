package com.github.topi314.lavasrc.deezer;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.util.List;

public class DeezerAudioPlaylist extends ExtendedAudioPlaylist {

  public DeezerAudioPlaylist(
    String name,
    List<AudioTrack> tracks,
    ExtendedAudioPlaylist.Type type,
    String identifier,
    String artworkURL,
    String author,
    Integer totalTracks
  ) {
    super(name, tracks, type, identifier, artworkURL, author, totalTracks);
  }
}
