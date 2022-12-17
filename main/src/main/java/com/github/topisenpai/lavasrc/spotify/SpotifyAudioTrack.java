package com.github.topisenpai.lavasrc.spotify;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class SpotifyAudioTrack extends MirroringAudioTrack {

	public SpotifyAudioTrack(AudioTrackInfo trackInfo, SpotifySourceManager sourceManager) {
		super(trackInfo, sourceManager);
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new SpotifyAudioTrack(this.trackInfo, (SpotifySourceManager) this.sourceManager);
	}

}
