package com.github.topislavalinkplugins.topissourcemanagers.spotify;

import com.github.topislavalinkplugins.topissourcemanagers.ISRCAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class SpotifyAudioTrack extends ISRCAudioTrack{

	public SpotifyAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, SpotifySourceManager sourceManager){
		super(trackInfo, isrc, artworkURL, sourceManager);
	}

	@Override
	protected AudioTrack makeShallowClone(){
		return new SpotifyAudioTrack(getInfo(), this.isrc, this.artworkURL, (SpotifySourceManager) this.sourceManager);
	}

}
