package com.github.topi314.lavasrc.source.spotify;

import com.github.topi314.lavasrc.extended.ExtendedAudioTrack;
import com.github.topi314.lavasrc.extended.ExtendedAudioTrackInfo;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.jetbrains.annotations.NotNull;

public class SpotifyAudioTrack extends MirroringAudioTrack<SpotifySourceManager> implements ExtendedAudioTrack {

	private final ExtendedAudioTrackInfo extendedTrackInfo;

	public SpotifyAudioTrack(AudioTrackInfo trackInfo, ExtendedAudioTrackInfo extendedTrackInfo, SpotifySourceManager sourceManager) {
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
		return new SpotifyAudioTrack(this.trackInfo, this.extendedTrackInfo, this.sourceManager);
	}

	public boolean isLocal() {
		return this.trackInfo.identifier.equals("local");
	}

}
