package com.github.topi314.lavasrc.pandora;

import com.github.topi314.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class PandoraAudioPlaylist extends ExtendedAudioPlaylist {
	public PandoraAudioPlaylist(String name, List<AudioTrack> tracks, ExtendedAudioPlaylist.Type type, String url, String artworkURL, String author, Integer totalTracks) {
		super(name, tracks, type, url, artworkURL, author, totalTracks);
	}
}
