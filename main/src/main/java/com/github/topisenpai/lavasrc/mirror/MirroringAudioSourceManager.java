package com.github.topisenpai.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;

public abstract class MirroringAudioSourceManager implements AudioSourceManager {

	public static final String ISRC_PATTERN = "%ISRC%";
	public static final String QUERY_PATTERN = "%QUERY%";

	protected final AudioPlayerManager audioPlayerManager;
	protected final MirroringAudioTrackResolver resolver;

	protected MirroringAudioSourceManager(AudioPlayerManager audioPlayerManager, MirroringAudioTrackResolver resolver) {
		this.audioPlayerManager = audioPlayerManager;
		this.resolver = resolver;
	}

	public AudioPlayerManager getAudioPlayerManager() {
		return this.audioPlayerManager;
	}

	public MirroringAudioTrackResolver getResolver() {
		return this.resolver;
	}

}
