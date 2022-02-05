package com.github.topislavalinkplugins.sources.applemusic;

import com.github.topislavalinkplugins.sources.ISRCAudioSourceManager;
import com.github.topislavalinkplugins.sources.ISRCAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.enums.ModelObjectType;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AppleMusicSourceManager extends ISRCAudioSourceManager{

	public static final Pattern APPLE_MUSIC_URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?music\\.apple\\.com/(?<countrycode>[a-zA-Z]{2}/)?(?<type>album|playlist|artist)(/[a-zA-Z\\-]+)?/(?<identifier>[a-zA-Z0-9.]+)(\\?i=(?<identifier2>\\d+))?");
	public static final String SEARCH_PREFIX = "amsearch:";
	public static final int MAX_PAGE_ITEMS = 300;

	private final AppleMusicClient appleMusicClient;

	public AppleMusicSourceManager(String[] providers, String country, AudioPlayerManager audioPlayerManager){
		super(providers, audioPlayerManager);
		this.appleMusicClient = new AppleMusicClient(country);
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
					throw new UnsupportedOperationException("Apple Music does not support artist pages");
					//return this.getArtist(id);
			}
		}
		catch(IOException | AppleMusicWebException e){
			throw new RuntimeException(e);
		}
		return null;
	}

	@Override
	public void shutdown(){

	}

	public AudioItem getSearch(String query) throws IOException, AppleMusicWebException{
		var searchResult = this.appleMusicClient.searchSongs(query, 25);

		if(searchResult.results.songs.data.size() == 0){
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		for(var item : searchResult.results.songs.data){
			tracks.add(ISRCAudioTrack.ofAppleMusic(item, this));
		}
		return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
	}

	public AudioItem getTrack(String id) throws IOException, AppleMusicWebException{
		return ISRCAudioTrack.ofAppleMusic(this.appleMusicClient.getSong(id).data.get(0), this);
	}

	public AudioItem getAlbum(String id) throws IOException, AppleMusicWebException{
		var album = this.appleMusicClient.getAlbum(id);
		var tracks = new ArrayList<AudioTrack>();

		for (var track : album.data.get(0).releationships.tracks.data) {
			tracks.add(ISRCAudioTrack.ofAppleMusic(track, this));
		}

		if (album.data.get(0).releationships.tracks.next != null) {
			Song.Wrapper paging = null;
			var offset = 100;
			do{
				paging = this.appleMusicClient.getAlbumSongs(id, MAX_PAGE_ITEMS, paging == null ? 0 : offset + MAX_PAGE_ITEMS);
				offset += MAX_PAGE_ITEMS;
				for(var item : paging.data){
					if(!item.type.equals("songs")){
						continue;
					}
					tracks.add(ISRCAudioTrack.ofAppleMusic(item, this));
				}
			}
			while(paging.next != null);
		}

		return new BasicAudioPlaylist(album.data.get(0).attributes.name, tracks, null, false);
	}

	public AudioItem getPlaylist(String id) throws IOException, AppleMusicWebException{
		var playlist = this.appleMusicClient.getPlaylist(id);
		var tracks = new ArrayList<AudioTrack>();

		for (var track : playlist.data.get(0).releationships.tracks.data) {
			tracks.add(ISRCAudioTrack.ofAppleMusic(track, this));
		}

		if (playlist.data.get(0).releationships.tracks.next != null){
			Song.Wrapper paging = null;
			var offset = 100;
			do{
				paging = this.appleMusicClient.getPlaylistSongs(id, MAX_PAGE_ITEMS, paging == null ? 0 : offset + MAX_PAGE_ITEMS);
				offset += MAX_PAGE_ITEMS;
				for(var item : paging.data){
					if(!item.type.equals("songs")){
						continue;
					}
					tracks.add(ISRCAudioTrack.ofAppleMusic(item, this));
				}
			}
			while(paging.next != null);
		}

		return new BasicAudioPlaylist(playlist.data.get(0).attributes.name, tracks, null, false);
	}

	/*
	public AudioItem getArtist(String id) throws IOException, ParseException, SpotifyWebApiException{
		var artist = this.appleMusicClient.getArtist(id).build().execute();
		var artistTracks = this.spotify.getArtistsTopTracks(id, this.config.getCountryCode()).build().execute();

		var tracks = new ArrayList<AudioTrack>();
		for(var track : artistTracks){
			tracks.add(ISRCAudioTrack.of(track, this));
		}

		return new BasicAudioPlaylist(artist.getName() + "'s Top Tracks", tracks, null, false);
	}*/

}
