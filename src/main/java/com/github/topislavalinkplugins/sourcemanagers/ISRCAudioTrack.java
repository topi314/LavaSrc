package com.github.topislavalinkplugins.sourcemanagers;

import com.github.topislavalinkplugins.sourcemanagers.applemusic.AppleMusicSourceManager;
import com.github.topislavalinkplugins.sourcemanagers.applemusic.Song;
import com.github.topislavalinkplugins.sourcemanagers.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
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

import static com.github.topislavalinkplugins.sourcemanagers.ISRCAudioSourceManager.ISRC_PATTERN;
import static com.github.topislavalinkplugins.sourcemanagers.ISRCAudioSourceManager.QUERY_PATTERN;

public class ISRCAudioTrack extends DelegatedAudioTrack{

	private static final Logger log = LoggerFactory.getLogger(ISRCAudioTrack.class);

	protected final String isrc;
	protected final String artworkURL;
	protected final ISRCAudioSourceManager sourceManager;

	public ISRCAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, ISRCAudioSourceManager sourceManager){
		super(trackInfo);
		this.isrc = isrc;
		this.artworkURL = artworkURL;
		this.sourceManager = sourceManager;
	}

	public static ISRCAudioTrack ofAppleMusic(Song song, AppleMusicSourceManager sourceManager){
		return new ISRCAudioTrack(new AudioTrackInfo(song.attributes.name, song.attributes.artistName, song.attributes.durationInMillis, song.id, false, song.attributes.url), song.attributes.isrc, song.attributes.artwork.getUrl(), sourceManager);
	}

	public static ISRCAudioTrack ofSpotify(String title, String identifier, String isrc, Image[] images, ArtistSimplified[] artists, Integer trackDuration, SpotifySourceManager spotifySourceManager){
		return new ISRCAudioTrack(new AudioTrackInfo(title,
			artists.length == 0 ? "unknown" : artists[0].getName(),
			trackDuration.longValue(),
			identifier,
			false,
			"https://open.spotify.com/track/" + identifier
		), isrc, images.length == 0 ? null : images[0].getUrl(), spotifySourceManager);
	}

	public static ISRCAudioTrack ofSpotify(TrackSimplified track, Album album, SpotifySourceManager spotifySourceManager){
		return ofSpotify(track.getName(), track.getId(), null, album.getImages(), track.getArtists(), track.getDurationMs(), spotifySourceManager);
	}

	public static ISRCAudioTrack ofSpotify(Track track, SpotifySourceManager spotifySourceManager){
		return ofSpotify(track.getName(), track.getId(), track.getExternalIds().getExternalIds().getOrDefault("isrc", null), track.getAlbum().getImages(), track.getArtists(), track.getDurationMs(), spotifySourceManager);
	}

	public String getISRC(){
		return this.isrc;
	}

	public String getArtworkURL(){
		return this.artworkURL;
	}

	private String getTrackTitle(){
		var query = this.trackInfo.title;
		if(!this.trackInfo.author.equals("unknown")){
			query += " " + this.trackInfo.author;
		}
		return query;
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception{
		AudioItem track = null;

		for(String provider : this.sourceManager.getProviders()){
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
		throw new FriendlyException("No matching track found", FriendlyException.Severity.COMMON, new TrackNotFoundException());
	}

	@Override
	public AudioSourceManager getSourceManager(){
		return this.sourceManager;
	}

	public AudioItem loadItem(String query){
		var cf = new CompletableFuture<AudioItem>();
		this.sourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler(){

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
		return cf.join();
	}

	@Override
	protected AudioTrack makeShallowClone(){
		return new ISRCAudioTrack(getInfo(), this.isrc, this.artworkURL, this.sourceManager);
	}

}
