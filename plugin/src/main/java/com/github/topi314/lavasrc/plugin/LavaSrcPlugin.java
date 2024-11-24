package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.tidal.TidalSourceManager;
import com.github.topi314.lavasrc.yandexmusic.YandexMusicSourceManager;
// import com.github.topi314.lavasrc.youtube.YoutubeSearchManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LavaSrcPlugin
  implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {

  private static final Logger log = LoggerFactory.getLogger(
    LavaSrcPlugin.class
  );

  private AudioPlayerManager manager;
  private SpotifySourceManager spotify;
  private AppleMusicSourceManager appleMusic;
  private DeezerAudioSourceManager deezer;
  private YandexMusicSourceManager yandexMusic;
  private FloweryTTSSourceManager flowerytts;
 // private YoutubeSearchManager youtube;
  private TidalSourceManager tidal;

  public LavaSrcPlugin(
          LavaSrcConfig pluginConfig,
          SourcesConfig sourcesConfig,
          SpotifyConfig spotifyConfig,
          TidalConfig tidalConfig,
          AppleMusicConfig appleMusicConfig,
          YandexMusicConfig yandexMusicConfig,
          FloweryTTSConfig floweryTTSConfig
  ) {
    log.info("Loading Mewwme LavaSrc...");

    if (sourcesConfig.isSpotify()) {
      this.spotify =
              new SpotifySourceManager(
                      pluginConfig.getProviders(),
                      spotifyConfig.getCountryCode(),
                      unused -> manager
              );
      if (spotifyConfig.getPlaylistLoadLimit() > 0) {
        this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
      }
      if (spotifyConfig.getAlbumLoadLimit() > 0) {
        this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
      }
    }
    if (sourcesConfig.isTidal()) {
      this.tidal =
              new TidalSourceManager(
                      pluginConfig.getProviders(),
                      tidalConfig.getCountryCode(),
                      unused -> manager
              );
      if (tidalConfig.getSearchLimit() > 0) {
        this.tidal.setSearchLimit(tidalConfig.getSearchLimit());
      }
    }
    if (sourcesConfig.isAppleMusic()) {
      this.appleMusic =
              new AppleMusicSourceManager(
                      pluginConfig.getProviders(),
                      appleMusicConfig.getMediaAPIToken(),
                      appleMusicConfig.getCountryCode(),
                      unused -> manager
              );
      if (appleMusicConfig.getPlaylistLoadLimit() > 0) {
        appleMusic.setPlaylistPageLimit(
                appleMusicConfig.getPlaylistLoadLimit()
        );
      }
      if (appleMusicConfig.getAlbumLoadLimit() > 0) {
        appleMusic.setAlbumPageLimit(appleMusicConfig.getAlbumLoadLimit());
      }
    }
    if (sourcesConfig.isDeezer()) {
      this.deezer = new DeezerAudioSourceManager();
    }
    if (sourcesConfig.isYandexMusic()) {
      this.yandexMusic =
              new YandexMusicSourceManager(yandexMusicConfig.getAccessToken());
    }
    if (sourcesConfig.isFloweryTTS()) {
      this.flowerytts =
              new FloweryTTSSourceManager(floweryTTSConfig.getVoice());
      if (floweryTTSConfig.getTranslate()) {
        this.flowerytts.setTranslate(floweryTTSConfig.getTranslate());
      }
      if (floweryTTSConfig.getSilence() > 0) {
        this.flowerytts.setSilence(floweryTTSConfig.getSilence());
      }
      if (floweryTTSConfig.getSpeed() > 0) {
        this.flowerytts.setSpeed(floweryTTSConfig.getSpeed());
      }
      if (floweryTTSConfig.getAudioFormat() != null) {
        this.flowerytts.setAudioFormat(floweryTTSConfig.getAudioFormat());
      }
    }
    /*
    if (sourcesConfig.isYoutube()) {
      if (hasNewYoutubeSource()) {
        log.info("Registering Youtube Source audio source manager...");
        this.youtube = new YoutubeSearchManager(() -> manager);
      } else {
        throw new IllegalStateException("Youtube LavaSearch requires the new Youtube Source plugin to be enabled.");
      }
    }
    */
  }

  /*
  private boolean hasNewYoutubeSource() {
    try {
      Class.forName("dev.lavalink.youtube.YoutubeAudioSourceManager");
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }
  */

  @NotNull
  @Override
  public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
    this.manager = manager;
    if (this.spotify != null) {
      log.info("Registering Spotify audio source manager...");
      manager.registerSourceManager(this.spotify);
    }
    if (this.appleMusic != null) {
      log.info("Registering Apple Music audio source manager...");
      manager.registerSourceManager(this.appleMusic);
    }
    if (this.deezer != null) {
      log.info("Registering Deezer audio source manager...");
      manager.registerSourceManager(this.deezer);
    }
    if (this.tidal != null) {
      log.info("Registering Tidal audio source manager...");
      manager.registerSourceManager(this.tidal);
    }
    if (this.yandexMusic != null) {
      log.info("Registering Yandex Music audio source manager...");
      manager.registerSourceManager(this.yandexMusic);
    }
    if (this.flowerytts != null) {
      log.info("Registering Flowery TTS audio source manager...");
      manager.registerSourceManager(this.flowerytts);
    }
    return manager;
  }

  @Override
  @NotNull
  public SearchManager configure(@NotNull SearchManager manager) {
    if (this.spotify != null) {
      log.info("Registering Spotify search manager...");
      manager.registerSearchManager(this.spotify);
    }
    if (this.appleMusic != null) {
      log.info("Registering Apple Music search manager...");
      manager.registerSearchManager(this.appleMusic);
    }
    if (this.deezer != null) {
      log.info("Registering Deezer search manager...");
      manager.registerSearchManager(this.deezer);
    }
    /*
    if (this.youtube != null) {
      log.info("Registering Youtube search manager...");
      manager.registerSearchManager(this.youtube);
    }
    */
    return manager;
  }
}
