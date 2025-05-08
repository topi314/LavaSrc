package com.github.topi314.lavasrc.source.tidal;

import com.github.topi314.lavasrc.extended.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class TidalAudioPlaylist extends ExtendedAudioPlaylist {
	public TidalAudioPlaylist(String name, List<AudioTrack> tracks, ExtendedAudioPlaylist.Type type, String url, String artworkURL, String author, Integer totalTracks) {
		super(name, tracks, type, url, artworkURL, author, totalTracks);
	}
}
