package com.github.topisenpai.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.DataOutput;
import java.io.IOException;

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

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
		var isrcAudioTrack = ((MirroringAudioTrack) track);
		DataFormatTools.writeNullableText(output, isrcAudioTrack.getISRC());
		DataFormatTools.writeNullableText(output, isrcAudioTrack.getArtworkURL());
	}

}
