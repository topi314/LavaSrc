package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.yandexmusic")
@Component
public class YandexMusicConfig {

  private String accessToken;

  public String getAccessToken() {
    return this.accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
}
