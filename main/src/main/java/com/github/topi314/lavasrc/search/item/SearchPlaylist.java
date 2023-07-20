package com.github.topi314.lavasrc.search.item;

public class SearchPlaylist {

	public final String identifier;
	public final String name;
	public final String url;
	public final String artworkUrl;
	public final String trackCount;

	public SearchPlaylist(String identifier, String name, String url, String artworkUrl, String trackCount) {
		this.identifier = identifier;
		this.name = name;
		this.url = url;
		this.artworkUrl = artworkUrl;
		this.trackCount = trackCount;
	}
}
