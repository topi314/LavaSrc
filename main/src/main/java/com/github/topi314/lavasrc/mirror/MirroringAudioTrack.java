package com.github.topi314.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MirroringAudioTrack<T extends MirroringAudioSourceManager> extends DelegatedAudioTrack {

	private static final Logger log = LoggerFactory.getLogger(MirroringAudioTrack.class);

	protected final T sourceManager;

	public MirroringAudioTrack(AudioTrackInfo trackInfo, T sourceManager) {
		super(trackInfo);
		this.sourceManager = sourceManager;
	}

	private static String getTrackId(AudioTrack track) {
		String id;
		if (track.getIdentifier() != null) {
			id = track.getIdentifier();
		} else {
			id = track.getInfo().uri;
		}
		return id + " (" + track.getSourceManager().getSourceName() + ")";
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		var track = this.sourceManager.getMirrorResolver().find(this);
		if (track == null) {
			throw new MirroringException();
		}

		log.debug("Loaded mirror track {} for {}", getTrackId(track), getTrackId(this));
		processDelegate((InternalAudioTrack) track, executor);
	}

	@Override
	public T getSourceManager() {
		return sourceManager;
	}

}
