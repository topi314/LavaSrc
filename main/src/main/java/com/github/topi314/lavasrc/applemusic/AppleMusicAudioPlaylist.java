package com.github.topi314.lavasrc.applemusic;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class AppleMusicAudioPlaylist extends ExtendedAudioPlaylist {

	public AppleMusicAudioPlaylist(String name, List<AudioTrack> tracks, ExtendedAudioPlaylist.Type type, String identifier, String artworkURL, String author) {
		super(name, tracks, type, identifier, artworkURL, author);
	}

}
