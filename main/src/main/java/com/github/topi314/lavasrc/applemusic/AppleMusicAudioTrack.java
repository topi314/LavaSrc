package com.github.topi314.lavasrc.applemusic;

import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class AppleMusicAudioTrack extends MirroringAudioTrack {

	public AppleMusicAudioTrack(AudioTrackInfo trackInfo, AppleMusicSourceManager sourceManager) {
		super(trackInfo, sourceManager);
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new AppleMusicAudioTrack(this.trackInfo, (AppleMusicSourceManager) this.sourceManager);
	}

}
