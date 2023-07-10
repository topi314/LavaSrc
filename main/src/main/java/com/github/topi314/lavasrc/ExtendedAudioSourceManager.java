package com.github.topi314.lavasrc;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

public abstract class ExtendedAudioSourceManager implements AudioSourceManager {

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
		var extendedTrack = (ExtendedAudioTrack) track;
		DataFormatTools.writeNullableText(output, extendedTrack.getAlbumName());
		DataFormatTools.writeNullableText(output, extendedTrack.getArtistArtworkUrl());
		DataFormatTools.writeNullableText(output, extendedTrack.getPreviewUrl());
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return true;
	}

	protected ExtendedAudioTrackInfo decodeTrack(DataInput input) throws IOException {
		String albumName = null;
		String artistArtworkUrl = null;
		String previewUrl = null;
		// Check if the input has more than 8 bytes available, which would indicate that the preview field is present.
		// This is done to avoid breaking backwards compatibility with tracks that were saved before the preview field was added.
		if (((DataInputStream) input).available() > Long.BYTES) {
			albumName = DataFormatTools.readNullableText(input);
			artistArtworkUrl = DataFormatTools.readNullableText(input);
			previewUrl = DataFormatTools.readNullableText(input);
		}
		return new ExtendedAudioTrackInfo(albumName, artistArtworkUrl, previewUrl);
	}

	protected static class ExtendedAudioTrackInfo {
		public final String albumName;
		public final String artistArtworkUrl;
		public final String previewUrl;

		public ExtendedAudioTrackInfo(String albumName, String artistArtworkUrl, String previewUrl) {
			this.albumName = albumName;
			this.artistArtworkUrl = artistArtworkUrl;
			this.previewUrl = previewUrl;
		}
	}
}
