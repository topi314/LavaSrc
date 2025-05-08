package com.github.topi314.lavasrc.source.applemusic;

import com.github.topi314.lavasrc.extended.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class AppleMusicAudioPlaylist extends ExtendedAudioPlaylist {

	public AppleMusicAudioPlaylist(String name, List<AudioTrack> tracks, ExtendedAudioPlaylist.Type type, String identifier, String artworkURL, String author, Integer totalTracks) {
		super(name, tracks, type, identifier, artworkURL, author, totalTracks);
	}

}
