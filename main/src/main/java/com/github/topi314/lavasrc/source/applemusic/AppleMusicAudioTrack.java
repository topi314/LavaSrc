package com.github.topi314.lavasrc.source.applemusic;

import com.github.topi314.lavasrc.extended.ExtendedAudioTrack;
import com.github.topi314.lavasrc.extended.ExtendedAudioTrackInfo;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.NotNull;

public class AppleMusicAudioTrack extends MirroringAudioTrack<AppleMusicSourceManager> implements ExtendedAudioTrack {

	private final ExtendedAudioTrackInfo extendedTrackInfo;

	public AppleMusicAudioTrack(AudioTrackInfo trackInfo, ExtendedAudioTrackInfo extendedTrackInfo, AppleMusicSourceManager sourceManager) {
		super(trackInfo, sourceManager);
		this.extendedTrackInfo = extendedTrackInfo;
	}

	@NotNull
	@Override
	public ExtendedAudioTrackInfo getExtendedInfo() {
		return this.extendedTrackInfo;
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new AppleMusicAudioTrack(this.trackInfo, this.extendedTrackInfo, this.sourceManager);
	}

}
