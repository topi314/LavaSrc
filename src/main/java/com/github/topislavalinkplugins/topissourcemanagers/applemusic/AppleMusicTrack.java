package com.github.topislavalinkplugins.topissourcemanagers.applemusic;

import com.github.topislavalinkplugins.topissourcemanagers.ISRCAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class AppleMusicTrack extends ISRCAudioTrack{

	public AppleMusicTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, AppleMusicSourceManager sourceManager){
		super(trackInfo, isrc, artworkURL, sourceManager);
	}

	@Override
	protected AudioTrack makeShallowClone(){
		return new AppleMusicTrack(getInfo(), this.isrc, this.artworkURL, (AppleMusicSourceManager) this.sourceManager);
	}

}
