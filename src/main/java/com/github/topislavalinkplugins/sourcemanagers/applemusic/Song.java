package com.github.topislavalinkplugins.sourcemanagers.applemusic;

import java.util.List;

public class Song{

	public String id, type, href;
	public Attributes attributes;

	public static class Attributes{

		public SongCollection.Attributes.Artwork artwork;
		public String artistName, url, name, isrc;
		public long durationInMillis;

	}

	public static class Wrapper{

		public String href, next;
		public List<Song> data;

	}

}
