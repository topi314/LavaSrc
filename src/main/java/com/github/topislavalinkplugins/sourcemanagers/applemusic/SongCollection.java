package com.github.topislavalinkplugins.sourcemanagers.applemusic;

import java.util.List;

public class SongCollection{

	public String id, type, href;
	public Attributes attributes;
	public Relationships relationships;

	public static class Attributes{

		public Artwork artwork;
		public String url, name;

		public static class Artwork{

			public int width, height;
			public String url;

			public String getUrl(){
				return this.url.replace("{w}", String.valueOf(width)).replace("{h}", String.valueOf(height));
			}

		}

	}

	public static class Relationships{

		public Song.Wrapper tracks;

	}

	public static class Wrapper{

		public String href, next;
		public List<SongCollection> data;

	}

}
