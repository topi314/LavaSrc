package com.github.topislavalinkplugins.spotify;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.model_objects.specification.Album;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github.topislavalinkplugins.spotify.SpotifySourceManager.ISRC_PATTERN;
import static com.github.topislavalinkplugins.spotify.SpotifySourceManager.QUERY_PATTERN;

public class SpotifyTrack extends DelegatedAudioTrack{

	private static final Logger log = LoggerFactory.getLogger(SpotifyTrack.class);

	private final String isrc;
	private final String artworkURL;
	private final SpotifySourceManager spotifySourceManager;

	public SpotifyTrack(String title, String identifier, String isrc, Image[] images, String uri, ArtistSimplified[] artists, Integer trackDuration, SpotifySourceManager spotifySourceManager){
		this(new AudioTrackInfo(title,
			artists.length == 0 ? "unknown" : artists[0].getName(),
			trackDuration.longValue(),
			identifier,
			false,
			"https://open.spotify.com/track/" + identifier
		), isrc, images.length == 0 ? null : images[0].getUrl(), spotifySourceManager);
	}

	public SpotifyTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, SpotifySourceManager spotifySourceManager){
		super(trackInfo);
		this.isrc = isrc;
		this.artworkURL = artworkURL;
		this.spotifySourceManager = spotifySourceManager;
	}

	public static SpotifyTrack of(TrackSimplified track, Album album, SpotifySourceManager spotifySourceManager){
		return new SpotifyTrack(track.getName(), track.getId(), null, album.getImages(), track.getUri(), track.getArtists(), track.getDurationMs(), spotifySourceManager);
	}

	public static SpotifyTrack of(Track track, SpotifySourceManager spotifySourceManager){
		return new SpotifyTrack(track.getName(), track.getId(), track.getExternalIds().getExternalIds().getOrDefault("isrc", null), track.getAlbum().getImages(), track.getUri(), track.getArtists(), track.getDurationMs(), spotifySourceManager);
	}

	public String getISRC(){
		return this.isrc;
	}

	public String getArtworkURL(){
		return this.artworkURL;
	}

	private String getTrackTitle(){
		var query = trackInfo.title;
		if(!trackInfo.author.equals("unknown")){
			query += " " + trackInfo.author;
		}
		return query;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception{
		var config = this.spotifySourceManager.getConfig();
		AudioItem track = null;

		for(String provider : config.getProviders()){
			if(provider.startsWith(SpotifySourceManager.SEARCH_PREFIX)){
				log.warn("Can not use spotify search as provider!");
				continue;
			}

			if(provider.contains(ISRC_PATTERN)){
				if(this.isrc != null){
					provider = provider.replace(ISRC_PATTERN, this.isrc);
				}
				else{
					log.debug("Ignoring identifier \"" + provider + "\" because this track does not have an ISRC!");
					continue;
				}
			}

			provider = provider.replace(QUERY_PATTERN, getTrackTitle());
			track = loadItem(provider);
			if(track != null){
				break;
			}
		}

		if(track instanceof AudioPlaylist){
			track = ((AudioPlaylist) track).getTracks().get(0);
		}
		if(track instanceof InternalAudioTrack){
			processDelegate((InternalAudioTrack) track, executor);
			return;
		}
		throw new FriendlyException("No matching Spotify track found", Severity.COMMON, new SpotifyTrackNotFoundException());
	}

	@Override
	public AudioSourceManager getSourceManager(){
		return this.spotifySourceManager;
	}

	public AudioItem loadItem(String query) throws ExecutionException, InterruptedException{
		var cf = new CompletableFuture<AudioItem>();
		this.spotifySourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler(){

			@Override
			public void trackLoaded(AudioTrack track){
				cf.complete(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist){
				cf.complete(playlist);
			}

			@Override
			public void noMatches(){
				cf.complete(null);
			}

			@Override
			public void loadFailed(FriendlyException exception){
				cf.completeExceptionally(exception);
			}
		});
		cf.join();
		return cf.get();
	}

	@Override
	protected AudioTrack makeShallowClone(){
		return new SpotifyTrack(getInfo(), isrc, artworkURL, spotifySourceManager);
	}

}
