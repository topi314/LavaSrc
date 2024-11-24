package com.github.topi314.lavasrc.yandexmusic;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

public class YandexMusicAudioTrack extends DelegatedAudioTrack {

  private final YandexMusicSourceManager sourceManager;

  public YandexMusicAudioTrack(
    AudioTrackInfo trackInfo,
    YandexMusicSourceManager sourceManager
  ) {
    super(trackInfo);
    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor executor) throws Exception {
    var downloadLink = this.getDownloadURL(this.trackInfo.identifier);
    try (var httpInterface = this.sourceManager.getHttpInterface()) {
      try (
        var stream = new PersistentHttpStream(
          httpInterface,
          new URI(downloadLink),
          this.trackInfo.length
        )
      ) {
        processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
      }
    }
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new YandexMusicAudioTrack(this.trackInfo, this.sourceManager);
  }

  @Override
  public AudioSourceManager getSourceManager() {
    return this.sourceManager;
  }

  private String getDownloadURL(String id)
    throws IOException, NoSuchAlgorithmException {
    var json =
      this.sourceManager.getJson(
          YandexMusicSourceManager.PUBLIC_API_BASE +
          "/tracks/" +
          id +
          "/download-info"
        );
    if (json.isNull() || json.get("result").values().isEmpty()) {
      throw new IllegalStateException("No download URL found for track " + id);
    }

    var downloadInfoLink = json
      .get("result")
      .values()
      .get(0)
      .get("downloadInfoUrl")
      .text();
    var downloadInfo = this.sourceManager.getDownloadStrings(downloadInfoLink);
    if (downloadInfo == null) {
      throw new IllegalStateException("No download URL found for track " + id);
    }

    var doc = Jsoup.parse(downloadInfo, "", Parser.xmlParser());
    var host = doc.select("host").text();
    var path = doc.select("path").text();
    var ts = doc.select("ts").text();
    var s = doc.select("s").text();

    var sign = "XGRlBW9FXlekgbPrRHuSiA" + path + s;
    var md = MessageDigest.getInstance("MD5");
    var digest = md.digest(sign.getBytes(StandardCharsets.UTF_8));
    var sb = new StringBuilder();
    for (byte b : digest) {
      sb.append(String.format("%02x", b));
    }
    var md5 = sb.toString();

    return "https://" + host + "/get-mp3/" + md5 + "/" + ts + path;
  }
}
