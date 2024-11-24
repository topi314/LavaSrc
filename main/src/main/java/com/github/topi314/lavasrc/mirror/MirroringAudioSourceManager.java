package com.github.topi314.lavasrc.mirror;

import com.github.topi314.lavasrc.ExtendedAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MirroringAudioSourceManager
        extends ExtendedAudioSourceManager
        implements HttpConfigurable {

  public static final String ISRC_PATTERN = "%ISRC%";
  public static final String QUERY_PATTERN = "%QUERY%";
  private static final Logger log = LoggerFactory.getLogger(
          MirroringAudioSourceManager.class
  );
  protected final Function<Void, AudioPlayerManager> audioPlayerManager;
  protected final MirroringAudioTrackResolver resolver;

  protected final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

  protected MirroringAudioSourceManager(
          AudioPlayerManager audioPlayerManager,
          MirroringAudioTrackResolver resolver
  ) {
    this(unused -> audioPlayerManager, resolver);
  }

  protected MirroringAudioSourceManager(
          Function<Void, AudioPlayerManager> audioPlayerManager,
          MirroringAudioTrackResolver resolver
  ) {
    this.audioPlayerManager = audioPlayerManager;
    this.resolver = resolver;
  }

  @Override
  public void configureRequests(
          Function<RequestConfig, RequestConfig> configurator
  ) {
    this.httpInterfaceManager.configureRequests(configurator);
  }

  @Override
  public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
    this.httpInterfaceManager.configureBuilder(configurator);
  }

  public HttpInterface getHttpInterface() {
    return this.httpInterfaceManager.getInterface();
  }

  @Override
  public void shutdown() {
    try {
      this.httpInterfaceManager.close();
    } catch (IOException e) {
      log.error("Failed to close HTTP interface manager", e);
    }
  }

  public AudioPlayerManager getAudioPlayerManager() {
    return this.audioPlayerManager.apply(null);
  }

  public MirroringAudioTrackResolver getResolver() {
    return this.resolver;
  }
}
