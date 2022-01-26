package com.github.topisenpai.plugin.spotify;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.ModelObjectType;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.readNullableText;
import static com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.writeNullableText;

public class SpotifySourceManager implements AudioSourceManager{

	public static final Pattern SPOTIFY_URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?open\\.spotify\\.com/(user/[a-zA-Z0-9-_]+/)?(?<type>track|album|playlist|artist)/(?<identifier>[a-zA-Z0-9-_]+)");
	public static final String SEARCH_PREFIX = "spsearch:";
	public static final int MAX_PAGE_ITEMS = 100;
	public static final String ISRC_PATTERN = "%ISRC%";
	public static final String QUERY_PATTERN = "%QUERY%";

	private static final Logger log = LoggerFactory.getLogger(SpotifySourceManager.class);

	private final SpotifyApi spotify;
	private final SpotifyConfig config;
	private final ClientCredentialsRequest clientCredentialsRequest;
	private final AudioPlayerManager audioPlayerManager;
	private final Thread thread;

	public SpotifySourceManager(SpotifyConfig config, AudioPlayerManager audioPlayerManager){
		if(config.getClientId() == null || config.getClientId().isEmpty()){
			throw new IllegalArgumentException("Spotify client id must be set");
		}
		if(config.getClientSecret() == null || config.getClientSecret().isEmpty()){
			throw new IllegalArgumentException("Spotify secret must be set");
		}
		this.config = config;
		this.audioPlayerManager = audioPlayerManager;
		this.spotify = new SpotifyApi.Builder().setClientId(config.getClientId()).setClientSecret(config.getClientSecret()).build();
		this.clientCredentialsRequest = this.spotify.clientCredentials().build();

		thread = new Thread(() -> {
			try{
				while(true){
					try{
						var clientCredentials = this.clientCredentialsRequest.execute();
						this.spotify.setAccessToken(clientCredentials.getAccessToken());
						Thread.sleep(clientCredentials.getExpiresIn() * 1000);
					}
					catch(IOException | SpotifyWebApiException | ParseException e){
						log.error("Failed to update the spotify access token. Retrying in 1 minute ", e);
						Thread.sleep(60 * 1000);
					}
				}
			}
			catch(Exception e){
				log.error("Failed to update the spotify access token", e);
			}
		});
		thread.setDaemon(true);
		thread.start();
	}

	public AudioPlayerManager getAudioPlayerManager(){
		return this.audioPlayerManager;
	}

	@Override
	public String getSourceName(){
		return "spotify";
	}

	@Override
	public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference){
		if(this.spotify == null){
			return null;
		}
		try{
			if(reference.identifier.startsWith(SEARCH_PREFIX)){
				return this.getSearch(reference.identifier.substring(SEARCH_PREFIX.length()).trim());
			}

			var matcher = SPOTIFY_URL_PATTERN.matcher(reference.identifier);
			if(!matcher.find()){
				return null;
			}

			var id = matcher.group("identifier");
			switch(matcher.group("type")){
				case "album":
					return this.getAlbum(id);

				case "track":
					return this.getTrack(id);

				case "playlist":
					return this.getPlaylist(id);

				case "artist":
					return this.getArtist(id);
			}
		}
		catch(IOException | ParseException | SpotifyWebApiException e){
			throw new RuntimeException(e);
		}
		return null;
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track){
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException{
		var spotifyTrack = (SpotifyTrack) track;
		writeNullableText(output, spotifyTrack.getISRC());
		writeNullableText(output, spotifyTrack.getArtworkURL());
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException{
		return new SpotifyTrack(trackInfo, readNullableText(input), readNullableText(input), this);
	}

	@Override
	public void shutdown(){
		this.thread.interrupt();
	}

	public AudioItem getSearch(String query) throws IOException, ParseException, SpotifyWebApiException{
		var searchResult = this.spotify.searchTracks(query).build().execute();

		if(searchResult.getItems().length == 0){
			return AudioReference.NO_TRACK;
		}

		var tracks = new ArrayList<AudioTrack>();
		for(var item : searchResult.getItems()){
			tracks.add(SpotifyTrack.of(item, this));
		}
		return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
	}

	public AudioItem getTrack(String id) throws IOException, ParseException, SpotifyWebApiException{
		return SpotifyTrack.of(this.spotify.getTrack(id).build().execute(), this);
	}

	public AudioItem getAlbum(String id) throws IOException, ParseException, SpotifyWebApiException{
		var album = this.spotify.getAlbum(id).build().execute();
		var tracks = new ArrayList<AudioTrack>();

		Paging<TrackSimplified> paging = null;
		do{
			paging = this.spotify.getAlbumsTracks(id).limit(MAX_PAGE_ITEMS).offset(paging == null ? 0 : paging.getOffset() + MAX_PAGE_ITEMS).build().execute();
			for(var track : paging.getItems()){
				tracks.add(SpotifyTrack.of(track, album, this));
			}
		}
		while(paging.getNext() != null);

		return new BasicAudioPlaylist(album.getName(), tracks, null, false);
	}

	public AudioItem getPlaylist(String id) throws IOException, ParseException, SpotifyWebApiException{
		var playlist = this.spotify.getPlaylist(id).build().execute();
		var tracks = new ArrayList<AudioTrack>();

		Paging<PlaylistTrack> paging = null;
		do{
			paging = this.spotify.getPlaylistsItems(id).limit(MAX_PAGE_ITEMS).offset(paging == null ? 0 : paging.getOffset() + MAX_PAGE_ITEMS).build().execute();
			for(var item : paging.getItems()){
				if(item.getIsLocal() || item.getTrack() == null || item.getTrack().getType() != ModelObjectType.TRACK){
					continue;
				}
				tracks.add(SpotifyTrack.of((Track) item.getTrack(), this));
			}
		}
		while(paging.getNext() != null);

		return new BasicAudioPlaylist(playlist.getName(), tracks, null, false);
	}

	public AudioItem getArtist(String id) throws IOException, ParseException, SpotifyWebApiException{
		var artist = this.spotify.getArtist(id).build().execute();
		var artistTracks = this.spotify.getArtistsTopTracks(id, this.config.getCountryCode()).build().execute();

		var tracks = new ArrayList<AudioTrack>();
		for(var track : artistTracks){
			tracks.add(SpotifyTrack.of(track, this));
		}

		return new BasicAudioPlaylist(artist.getName() + "'s Top Tracks", tracks, null, false);
	}

	public SpotifyConfig getConfig(){
		return config;
	}

}
