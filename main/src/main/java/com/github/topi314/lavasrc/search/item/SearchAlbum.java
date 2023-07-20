package com.github.topi314.lavasrc.search.item;

import com.github.topi314.lavasrc.search.SearchItem;

public class SearchAlbum implements SearchItem {

	public final String identifier;
	public final String name;
	public final String artist;
	public final String url;
	public final String artworkUrl;
	public final String trackCount;
	public final String isrc;

	public SearchAlbum(String identifier, String name, String artist, String url, String artworkUrl, String trackCount, String isrc) {
		this.identifier = identifier;
		this.name = name;
		this.artist = artist;
		this.url = url;
		this.artworkUrl = artworkUrl;
		this.trackCount = trackCount;
		this.isrc = isrc;
	}
}
