package com.github.topi314.lavasrc.spotify;

import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public class SpotifyAudioTrack extends MirroringAudioTrack {


	public SpotifyAudioTrack(AudioTrackInfo trackInfo, SpotifySourceManager sourceManager) {
		this(trackInfo, null, null, null, sourceManager);
	}

	public SpotifyAudioTrack(AudioTrackInfo trackInfo, String albumName, String artistArtworkUrl, String previewUrl, SpotifySourceManager sourceManager) {
		super(trackInfo, albumName, artistArtworkUrl, previewUrl, sourceManager);
	}

	@Override
	protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
		return new Mp3AudioTrack(trackInfo, stream);
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new SpotifyAudioTrack(this.trackInfo, (SpotifySourceManager) this.sourceManager);
	}

}
