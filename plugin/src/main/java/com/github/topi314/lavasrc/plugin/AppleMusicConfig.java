package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.applemusic")
@Component
public class AppleMusicConfig {

  private String countryCode = "us";
  private String musicKitKey;
  private String teamID;
  private String keyID;

  private String mediaAPIToken;
  private int playlistLoadLimit;
  private int albumLoadLimit;

  public String getCountryCode() {
    return this.countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getMediaAPIToken() {
    return this.mediaAPIToken;
  }

  public void setMediaAPIToken(String mediaAPIToken) {
    this.mediaAPIToken = mediaAPIToken;
  }

  public int getPlaylistLoadLimit() {
    return this.playlistLoadLimit;
  }

  public void setPlaylistLoadLimit(int playlistLoadLimit) {
    this.playlistLoadLimit = playlistLoadLimit;
  }

  public int getAlbumLoadLimit() {
    return this.albumLoadLimit;
  }

  public void setAlbumLoadLimit(int albumLoadLimit) {
    this.albumLoadLimit = albumLoadLimit;
  }

  public String getMusicKitKey() {
    return musicKitKey;
  }

  public void setMusicKitKey(String musicKitKey) {
    this.musicKitKey = musicKitKey;
  }

  public String getTeamID() {
    return teamID;
  }

  public void setTeamID(String teamID) {
    this.teamID = teamID;
  }

  public String getKeyID() {
    return keyID;
  }

  public void setKeyID(String keyID) {
    this.keyID = keyID;
  }
}
