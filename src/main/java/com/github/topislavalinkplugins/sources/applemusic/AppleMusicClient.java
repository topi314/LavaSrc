package com.github.topislavalinkplugins.sources.applemusic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public class AppleMusicClient{

	private static final Logger log = LoggerFactory.getLogger(AppleMusicClient.class);

	private final ObjectMapper jackson;
	private final String country;
	private String token;
	private Instant tokenExpire;

	public AppleMusicClient(String country){
		this.jackson = new ObjectMapper();
		this.country = country;
		this.updateToken();
	}

	public void updateToken(){
		try{
			this.token = getToken();
			System.out.println("TOKEN: " + this.token);
			this.tokenExpire = getTokenExpire();
		}
		catch(IOException | AppleMusicWebException e){
			log.error("Failed to get Apple Music token", e);
		}
	}

	public String getToken() throws IOException, AppleMusicWebException{
		var document = Jsoup.parse(request("GET", null, null, "https://music.apple.com/cy/album/animals/1533388849"), null, "https://music.apple.com/");
		return jackson.readTree(URLDecoder.decode(document.selectFirst("meta[name=desktop-music-app/config/environment]").attr("content"), StandardCharsets.UTF_8)).get("MEDIA_API").get("token").asText();
	}

	public Instant getTokenExpire() throws IOException, AppleMusicWebException{
		return Instant.ofEpochSecond(jackson.readTree(Base64.getDecoder().decode(this.token.split("\\.")[1])).get("exp").asLong());
	}

	public void checkToken(){
		if(this.tokenExpire.isBefore(Instant.now())){
			return;
		}
		this.updateToken();
	}

	public Map<String, String> getHeaders(){
		return Map.of("Authorization", "Bearer " + this.token);
	}

	public SearchResult searchSongs(String query, int limit) throws IOException, AppleMusicWebException{
		this.checkToken();

		return getClass(SearchResult.class, getHeaders(), "https://api.music.apple.com/v1/catalog/%s/search?term=%s&limit=%d", this.country, URLEncoder.encode(query, StandardCharsets.UTF_8), limit);
	}

	public Song.Wrapper getSong(String id) throws IOException, AppleMusicWebException{
		this.checkToken();

		return getClass(Song.Wrapper.class, getHeaders(), "https://api.music.apple.com/v1/catalog/%s/songs/%s", this.country, id);
	}

	public SongCollection.Wrapper getAlbum(String id) throws IOException, AppleMusicWebException{
		this.checkToken();

		return getClass(SongCollection.Wrapper.class, getHeaders(), "https://api.music.apple.com/v1/catalog/%s/albums/%s", this.country, id);
	}

	public Song.Wrapper getAlbumSongs(String id, int limit, int offset) throws IOException, AppleMusicWebException{
		this.checkToken();

		return getClass(Song.Wrapper.class, getHeaders(), "https://api.music.apple.com/v1/catalog/%s/albums/%s/tracks?limit=%d&offset=%d", this.country, id, limit, offset);
	}

	public SongCollection.Wrapper getPlaylist(String id) throws IOException, AppleMusicWebException{
		this.checkToken();

		return getClass(SongCollection.Wrapper.class, getHeaders(), "https://api.music.apple.com/v1/catalog/%s/playlists/%s", this.country, id);
	}

	public Song.Wrapper getPlaylistSongs(String id, int limit, int offset) throws IOException, AppleMusicWebException{
		this.checkToken();

		return getClass(Song.Wrapper.class, getHeaders(), "https://api.music.apple.com/v1/catalog/%s/playlists/%s/tracks?limit=%d&offset=%d", this.country, id, limit, offset);
	}


	public <T> T getClass(Class<T> clazz, Map<String, String> headers, String url, Object... params) throws IOException, AppleMusicWebException{
		return jackson.readValue(request("GET", headers, null, url, params), clazz);
	}

	public InputStream request(String method, Map<String, String> headers, String body, String route, Object... params) throws IOException, AppleMusicWebException{
		var con = (HttpURLConnection) new URL(String.format(route, params)).openConnection();
		con.setRequestMethod(method);
		if(headers != null){
			for(var entry : headers.entrySet()){
				con.setRequestProperty(entry.getKey(), entry.getValue());
			}
		}
		if(body != null){
			con.setDoOutput(true);
			try(var os = con.getOutputStream()){
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}
		}
		else{
			con.connect();
		}
		con.disconnect();
		if(con.getResponseCode() != 200){
			throw new AppleMusicWebException(con.getResponseMessage());
		}
		return con.getInputStream();
	}

}
