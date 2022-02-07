package com.github.topislavalinkplugins.topissourcemanagers.applemusic;

import com.github.topislavalinkplugins.topissourcemanagers.ISRCAudioSourceManager;
import com.github.topislavalinkplugins.topissourcemanagers.ISRCAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class AppleMusicSourceManager extends ISRCAudioSourceManager implements HttpConfigurable{

	private static final Logger log = LoggerFactory.getLogger(AppleMusicSourceManager.class);

	public static final Pattern APPLE_MUSIC_URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?music\\.apple\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>album|playlist|artist)(/[a-zA-Z0-9\\-]+)?/(?<identifier>[a-zA-Z0-9.]+)(\\?i=(?<identifier2>\\d+))?");
	public static final String SEARCH_PREFIX = "amsearch:";
	public static final int MAX_PAGE_ITEMS = 300;

	private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
	private final String catalog = "us";

	public AppleMusicSourceManager(String[] providers, String country, AudioPlayerManager audioPlayerManager){
		super(providers, audioPlayerManager);
	}

	@Override
	public String getSourceName(){
		return "applemusic";
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference){
		try{
			if(reference.identifier.startsWith(SEARCH_PREFIX)){
				return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
			}

			var matcher = APPLE_MUSIC_URL_PATTERN.matcher(reference.identifier);
			if(!matcher.find()){
				return null;
			}

			var id = matcher.group("identifier");
			switch(matcher.group("type")){
				case "album":
					var id2 = matcher.group("identifier2");
					if(id2 == null || id2.isEmpty()){
						return this.getAlbum(id);
					}
					return this.getTrack(id2);

				case "playlist":
					return this.getPlaylist(id);

				case "artist":
					return this.getArtist(id);
			}
		}
		catch(IOException | AppleMusicWebAPIException e){
			throw new RuntimeException(e);
		}
		return null;
	}

	public AudioItem getSearch(String query) throws IOException{
		var uri = "https://api.music.apple.com/v1/catalog/"+catalog+"/search?term=" + query + "&limit=" + 25;
		try(var response = this.httpInterfaceManager.getInterface().execute(new HttpGet(uri)){
			if (response.getStatusLine().getStatusCode() != 200){
				throw new IOException("HTTP error " + response.getStatusLine().getStatusCode() + ": " + response.getStatusLine().getReasonPhrase());
			}
			var json = JsonBrowser.parse(response.getEntity().getContent());
			json.get("data").
		}


	}

	@Override
	public void shutdown(){
		try{
			this.httpInterfaceManager.close();
		}
		catch(IOException e){
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	@Override
	public void configureRequests(Function<RequestConfig, RequestConfig> configurator){
		this.httpInterfaceManager.configureRequests(configurator);
	}

	@Override
	public void configureBuilder(Consumer<HttpClientBuilder> configurator){
		this.httpInterfaceManager.configureBuilder(configurator);
	}

}
