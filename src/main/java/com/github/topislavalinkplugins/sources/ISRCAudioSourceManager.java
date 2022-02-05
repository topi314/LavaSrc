package com.github.topislavalinkplugins.sources;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class ISRCAudioSourceManager implements AudioSourceManager{

	public static final String ISRC_PATTERN = "%ISRC%";
	public static final String QUERY_PATTERN = "%QUERY%";
	protected final AudioPlayerManager audioPlayerManager;
	protected String[] providers = {
		"ytsearch:\"" + ISRC_PATTERN + "\"",
		"ytsearch:" + QUERY_PATTERN
	};

	protected ISRCAudioSourceManager(String[] providers, AudioPlayerManager audioPlayerManager){
		if(providers != null && providers.length > 0){
			this.providers = providers;
		}
		this.audioPlayerManager = audioPlayerManager;
	}

	public String[] getProviders(){
		return this.providers;
	}

	public AudioPlayerManager getAudioPlayerManager(){
		return this.audioPlayerManager;
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track){
		return true;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException{
		var isrcAudioTrack = ((ISRCAudioTrack) track);
		DataFormatTools.writeNullableText(output, isrcAudioTrack.getISRC());
		DataFormatTools.writeNullableText(output, isrcAudioTrack.getArtworkURL());
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException{
		return new ISRCAudioTrack(trackInfo, DataFormatTools.readNullableText(input), DataFormatTools.readNullableText(input), this);
	}

}
