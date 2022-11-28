package com.github.topisenpai.lavasrc.spotify;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import java.util.List;

public class SpotifyAudioPlaylist extends BasicAudioPlaylist {

	private final String type;
	private final String identifier;
	private final String artworkURL;
	private final String author;

	public SpotifyAudioPlaylist(String name, List<AudioTrack> tracks, String type, String identifier, String artworkURL, String author) {
		super(name, tracks, null, false);
		this.type = type;
		this.identifier = identifier;
		this.artworkURL = artworkURL;
		this.author = author;
	}

	public String getType() {
		return type;
	}

	public String getIdentifier() {
		return this.identifier;
	}

	public String getArtworkURL() {
		return this.artworkURL;
	}

	public String getAuthor() {
		return this.author;
	}

}
