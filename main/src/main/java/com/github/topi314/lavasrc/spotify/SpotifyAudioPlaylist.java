package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class SpotifyAudioPlaylist extends ExtendedAudioPlaylist {

	public SpotifyAudioPlaylist(String name, List<AudioTrack> tracks, String type, String url, String artworkURL, String author) {
		super(name, tracks, type, url, artworkURL, author);
	}

}
