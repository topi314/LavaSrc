package com.github.topi314.lavasrc.applemusic;

import com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topi314.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;

public class AppleMusicAudioTrack extends MirroringAudioTrack {

  public AppleMusicAudioTrack(
    AudioTrackInfo trackInfo,
    AppleMusicSourceManager sourceManager
  ) {
    this(trackInfo, null, null, null, null, null, false, sourceManager);
  }

  public AppleMusicAudioTrack(
    AudioTrackInfo trackInfo,
    String albumName,
    String albumUrl,
    String artistUrl,
    String artistArtworkUrl,
    String previewUrl,
    boolean isPreview,
    MirroringAudioSourceManager sourceManager
  ) {
    super(
      trackInfo,
      albumName,
      albumUrl,
      artistUrl,
      artistArtworkUrl,
      previewUrl,
      isPreview,
      sourceManager
    );
  }

  @Override
  protected InternalAudioTrack createAudioTrack(
    AudioTrackInfo trackInfo,
    SeekableInputStream stream
  ) {
    return new MpegAudioTrack(trackInfo, stream);
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new AppleMusicAudioTrack(
      this.trackInfo,
      (AppleMusicSourceManager) this.sourceManager
    );
  }
}
