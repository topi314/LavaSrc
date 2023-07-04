package com.github.topi314.lavasrc;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import java.util.List;

public class ExtendedAudioPlaylist extends BasicAudioPlaylist {
	private final String type;
	private final String url;
	private final String artworkURL;
	private final String author;

	public ExtendedAudioPlaylist(String name, List<AudioTrack> tracks, String type, String url, String artworkURL, String author) {
		super(name, tracks, null, false);
		this.type = type;
		this.url = url;
		this.artworkURL = artworkURL;
		this.author = author;
	}

	public String getType() {
		return type;
	}

	public String getUrl() {
		return this.url;
	}

	public String getArtworkURL() {
		return this.artworkURL;
	}

	public String getAuthor() {
		return this.author;
	}

}
