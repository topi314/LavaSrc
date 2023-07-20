package com.github.topi314.lavasrc.search;

import com.github.topi314.lavasrc.search.item.*;

import java.util.List;

public class SearchResult {

	public static final SearchResult EMPTY = new SearchResult(null, null, null, null, null);

	public final List<SearchAlbum> albums;
	public final List<SearchArtist> artists;
	public final List<SearchPlaylist> playlists;
	public final List<SearchText> texts;
	public final List<SearchTrack> tracks;

	public SearchResult(List<SearchAlbum> albums, List<SearchArtist> artists, List<SearchPlaylist> playlists, List<SearchText> texts, List<SearchTrack> tracks) {
		this.albums = albums;
		this.artists = artists;
		this.playlists = playlists;
		this.texts = texts;
		this.tracks = tracks;
	}

}
