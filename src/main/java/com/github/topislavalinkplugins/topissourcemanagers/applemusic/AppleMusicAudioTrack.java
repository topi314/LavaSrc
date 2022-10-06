package com.github.topislavalinkplugins.topissourcemanagers.applemusic;

import com.github.topislavalinkplugins.topissourcemanagers.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class AppleMusicAudioTrack extends MirroringAudioTrack{

	public AppleMusicAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, AppleMusicSourceManager sourceManager){
		super(trackInfo, isrc, artworkURL, sourceManager);
	}

	@Override
	protected AudioTrack makeShallowClone(){
		return new AppleMusicAudioTrack(this.trackInfo, this.isrc, this.artworkURL, (AppleMusicSourceManager) this.sourceManager);
	}

}
