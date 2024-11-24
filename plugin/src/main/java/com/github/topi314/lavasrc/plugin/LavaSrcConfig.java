package com.github.topi314.lavasrc.plugin;

import static com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager.ISRC_PATTERN;
import static com.github.topi314.lavasrc.mirror.MirroringAudioSourceManager.QUERY_PATTERN;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc")
@Component
public class LavaSrcConfig {

  private String[] providers = {
    "ytsearch:\"" + ISRC_PATTERN + "\"",
    "ytsearch:" + QUERY_PATTERN,
  };

  public String[] getProviders() {
    return this.providers;
  }

  public void setProviders(String[] providers) {
    this.providers = providers;
  }
}
