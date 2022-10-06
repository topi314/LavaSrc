package com.github.topislavalinkplugins.topissourcemanagers.spotify;

import com.github.topislavalinkplugins.topissourcemanagers.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class SpotifyAudioTrack extends MirroringAudioTrack{

	public SpotifyAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, SpotifySourceManager sourceManager){
		super(trackInfo, isrc, artworkURL, sourceManager);
	}

	@Override
	protected AudioTrack makeShallowClone(){
		return new SpotifyAudioTrack(this.trackInfo, this.isrc, this.artworkURL, (SpotifySourceManager) this.sourceManager);
	}

}
