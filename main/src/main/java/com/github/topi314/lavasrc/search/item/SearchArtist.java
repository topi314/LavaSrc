package com.github.topi314.lavasrc.search.item;

import com.github.topi314.lavasrc.search.SearchItem;

public class SearchArtist implements SearchItem {

	public final String identifier;
	public final String name;
	public final String url;
	public final String artworkUrl;

	public SearchArtist(String identifier, String name, String url, String artworkUrl) {
		this.identifier = identifier;
		this.name = name;
		this.url = url;
		this.artworkUrl = artworkUrl;
	}
}
