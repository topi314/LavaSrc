package com.github.topi314.lavasrc.extended;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface ExtendedAudioSourceManager extends AudioSourceManager {

	default void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
		var info = ((ExtendedAudioTrack) track).getExtendedInfo();

		var json = JsonBrowser.newMap();
		json.put("albumName", info.albumName);
		json.put("albumUrl", info.albumUrl);
		json.put("artistUrl", info.artistUrl);
		json.put("artistArtworkUrl", info.artistArtworkUrl);
		json.put("isPreview", info.isPreview);

		DataFormatTools.writeNullableText(output, json.format());
	}

	default boolean isTrackEncodable(AudioTrack track) {
		return track instanceof ExtendedAudioTrack;
	}

	default ExtendedAudioTrackInfo decodeTrack(DataInput input) throws IOException {
		var rawJson = DataFormatTools.readNullableText(input);
		if (rawJson == null) {
			return null;
		}
		var json = JsonBrowser.parse(rawJson);

		return new ExtendedAudioTrackInfo(
			json.get("albumName").text(),
			json.get("albumUrl").text(),
			json.get("artistUrl").text(),
			json.get("artistArtworkUrl").text(),
			json.get("isPreview").asBoolean(false)
		);
	}

}
