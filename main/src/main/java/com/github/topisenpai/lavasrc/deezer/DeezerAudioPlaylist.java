package com.github.topisenpai.lavasrc.deezer;

import com.github.topisenpai.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class DeezerAudioPlaylist extends ExtendedAudioPlaylist {

	public DeezerAudioPlaylist(String name, List<AudioTrack> tracks, String type, String identifier, String artworkURL, String author) {
		super(name, tracks, type, identifier, artworkURL, author);
	}

}
