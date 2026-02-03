package com.github.topi314.lavasrc.pandora;

import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;

public class PandoraAudioTrack extends MirroringAudioTrack {

	public PandoraAudioTrack(AudioTrackInfo trackInfo, PandoraSourceManager sourceManager) {
		this(trackInfo, null, null, null, null, null, false, sourceManager);
	}

	public PandoraAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringAudioSourceManager sourceManager) {
		super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
	}

	@Override
	protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
		throw new UnsupportedOperationException("Previews are not supported by Pandora");
	}

	@Override
	protected AudioTrack makeShallowClone() {
		return new PandoraAudioTrack(this.trackInfo, (PandoraSourceManager) this.sourceManager);
	}
}