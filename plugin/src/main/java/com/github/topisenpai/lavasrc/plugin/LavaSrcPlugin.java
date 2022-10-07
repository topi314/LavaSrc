package com.github.topisenpai.lavasrc.plugin;

import com.github.topisenpai.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topisenpai.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topisenpai.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LavaSrcPlugin implements AudioPlayerManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LavaSrcPlugin.class);

    private final LavaSrcConfig pluginConfig;
    private final SourcesConfig sourcesConfig;
    private final SpotifyConfig spotifyConfig;
    private final AppleMusicConfig appleMusicConfig;

    private final DeezerConfig deezerConfig;

    public LavaSrcPlugin(LavaSrcConfig pluginConfig, SourcesConfig sourcesConfig, SpotifyConfig spotifyConfig, AppleMusicConfig appleMusicConfig, DeezerConfig deezerConfig) {
        log.info("Loading LavaSrc-Plugin...");
        this.pluginConfig = pluginConfig;
        this.sourcesConfig = sourcesConfig;
        this.spotifyConfig = spotifyConfig;
        this.appleMusicConfig = appleMusicConfig;
        this.deezerConfig = deezerConfig;
    }

    @Override
    public AudioPlayerManager configure(AudioPlayerManager manager) {
        if (this.sourcesConfig.isSpotify()) {
            log.info("Loading Spotify-SourceManager...");
            manager.registerSourceManager(new SpotifySourceManager(this.pluginConfig.getProviders(), this.spotifyConfig.getClientId(), this.spotifyConfig.getClientSecret(), manager));
        }
        if (this.sourcesConfig.isAppleMusic()) {
            log.info("Loading Apple-Music-SourceManager...");
            manager.registerSourceManager(new AppleMusicSourceManager(this.pluginConfig.getProviders(), this.appleMusicConfig.getCountryCode(), manager));
        }
        if (this.sourcesConfig.isDeezer()) {
            log.info("Loading Deezer-SourceManager...");
            manager.registerSourceManager(new DeezerAudioSourceManager(this.deezerConfig.getMasterDecryptionKey()));
        }
        return manager;
    }

}
