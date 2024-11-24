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
  public void encodeTrack(AudioTrack track, DataOutput output)
    throws IOException {
    var extendedTrack = (ExtendedAudioTrack) track;
    DataFormatTools.writeNullableText(output, extendedTrack.getAlbumName());
    DataFormatTools.writeNullableText(output, extendedTrack.getAlbumUrl());
    DataFormatTools.writeNullableText(output, extendedTrack.getArtistUrl());
    DataFormatTools.writeNullableText(
      output,
      extendedTrack.getArtistArtworkUrl()
    );
    DataFormatTools.writeNullableText(output, extendedTrack.getPreviewUrl());
    output.writeBoolean(extendedTrack.isPreview());
  }

  @Override
  public boolean isTrackEncodable(AudioTrack track) {
    return true;
  }

  protected ExtendedAudioTrackInfo decodeTrack(DataInput input)
    throws IOException {
    String albumName = null;
    String albumUrl = null;
    String artistUrl = null;
    String artistArtworkUrl = null;
    String previewUrl = null;
    boolean isPreview = false;
    // Check if the input has more than 8 bytes available, which would indicate that the preview field is present.
    // This is done to avoid breaking backwards compatibility with tracks that were saved before the preview field was added.
    if (((DataInputStream) input).available() > Long.BYTES) {
      albumName = DataFormatTools.readNullableText(input);
      albumUrl = DataFormatTools.readNullableText(input);
      artistUrl = DataFormatTools.readNullableText(input);
      artistArtworkUrl = DataFormatTools.readNullableText(input);
      previewUrl = DataFormatTools.readNullableText(input);
      isPreview = input.readBoolean();
    }
    return new ExtendedAudioTrackInfo(
      albumName,
      albumUrl,
      artistArtworkUrl,
      previewUrl,
      artistUrl,
      isPreview
    );
  }

  protected static class ExtendedAudioTrackInfo {

    public final String albumName;
    public final String albumUrl;
    public final String artistArtworkUrl;
    public final String previewUrl;
    public final String artistUrl;
    public final boolean isPreview;

    public ExtendedAudioTrackInfo(
      String albumName,
      String albumUrl,
      String artistArtworkUrl,
      String previewUrl,
      String artistUrl,
      boolean isPreview
    ) {
      this.albumName = albumName;
      this.albumUrl = albumUrl;
      this.artistArtworkUrl = artistArtworkUrl;
      this.previewUrl = previewUrl;
      this.artistUrl = artistUrl;
      this.isPreview = isPreview;
    }
  }
}
