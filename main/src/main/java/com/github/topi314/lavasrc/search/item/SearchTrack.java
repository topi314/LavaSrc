package com.github.topi314.lavasrc.search.item;

import com.github.topi314.lavasrc.search.SearchItem;

public class SearchTrack implements SearchItem {

	public final String title;
	public final String author;
	public final long length;
	public final String identifier;
	public final boolean isStream;
	public final String uri;
	public final String artworkUrl;
	public final String isrc;

	public SearchTrack(String title, String author, long length, String identifier, boolean isStream, String uri, String artworkUrl, String isrc) {
		this.title = title;
		this.author = author;
		this.length = length;
		this.identifier = identifier;
		this.isStream = isStream;
		this.uri = uri;
		this.artworkUrl = artworkUrl;
		this.isrc = isrc;
	}
}
