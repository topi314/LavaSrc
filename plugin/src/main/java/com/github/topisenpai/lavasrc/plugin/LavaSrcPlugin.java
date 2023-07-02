package com.github.topisenpai.lavasrc.plugin;

import com.github.topisenpai.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topisenpai.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topisenpai.lavasrc.spotify.SpotifySourceManager;
import com.github.topisenpai.lavasrc.yandexmusic.YandexMusicSourceManager;
import com.github.topisenpai.lavasrc.flowerytts.FloweryTTSSourceManager;
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
	private final YandexMusicConfig yandexMusicConfig;
	private final FloweryTTSConfig floweryTTSConfig;

	private final DeezerConfig deezerConfig;

	public LavaSrcPlugin(LavaSrcConfig pluginConfig, SourcesConfig sourcesConfig, SpotifyConfig spotifyConfig, AppleMusicConfig appleMusicConfig, DeezerConfig deezerConfig, YandexMusicConfig yandexMusicConfig, FloweryTTSConfig floweryTTSConfig ) {
		log.info("Loading LavaSrc plugin...");
		this.pluginConfig = pluginConfig;
		this.sourcesConfig = sourcesConfig;
		this.spotifyConfig = spotifyConfig;
		this.appleMusicConfig = appleMusicConfig;
		this.deezerConfig = deezerConfig;
		this.yandexMusicConfig = yandexMusicConfig;
		this.floweryTTSConfig = floweryTTSConfig;
	}

	@Override
	public AudioPlayerManager configure(AudioPlayerManager manager) {
		if (this.sourcesConfig.isSpotify()) {
			log.info("Registering Spotify audio source manager...");
			var spotifySourceManager = new SpotifySourceManager(this.pluginConfig.getProviders(), this.spotifyConfig.getClientId(), this.spotifyConfig.getClientSecret(), this.spotifyConfig.getCountryCode(), manager);
			if (this.spotifyConfig.getPlaylistLoadLimit() > 0) {
				spotifySourceManager.setPlaylistPageLimit(this.spotifyConfig.getPlaylistLoadLimit());
			}
			if (this.spotifyConfig.getAlbumLoadLimit() > 0) {
				spotifySourceManager.setAlbumPageLimit(this.spotifyConfig.getAlbumLoadLimit());
			}
			manager.registerSourceManager(spotifySourceManager);
		}
		if (this.sourcesConfig.isAppleMusic()) {
			log.info("Registering Apple Music audio source manager...");
			var appleMusicSourceManager = new AppleMusicSourceManager(this.pluginConfig.getProviders(), this.appleMusicConfig.getMediaAPIToken(), this.appleMusicConfig.getCountryCode(), manager);
			if (this.appleMusicConfig.getPlaylistLoadLimit() > 0) {
				appleMusicSourceManager.setPlaylistPageLimit(this.appleMusicConfig.getPlaylistLoadLimit());
			}
			if (this.appleMusicConfig.getAlbumLoadLimit() > 0) {
				appleMusicSourceManager.setAlbumPageLimit(this.appleMusicConfig.getAlbumLoadLimit());
			}
			manager.registerSourceManager(appleMusicSourceManager);
		}
		if (this.sourcesConfig.isDeezer()) {
			log.info("Registering Deezer audio source manager...");
			manager.registerSourceManager(new DeezerAudioSourceManager(this.deezerConfig.getMasterDecryptionKey()));
		}
		if (this.sourcesConfig.isYandexMusic()) {
			log.info("Registering Yandex Music audio source manager...");
			manager.registerSourceManager(new YandexMusicSourceManager(this.yandexMusicConfig.getAccessToken()));
		}
		if (this.sourcesConfig.isFloweryTTS()) {
			log.info("Registering Flowery TTS audio source manager...");
			var floweryTTSSourceManager = new FloweryTTSSourceManager(this.floweryTTSConfig.getVoice());
			floweryTTSSourceManager.setTranslate(this.floweryTTSConfig.getTranslate());
			floweryTTSSourceManager.setSilence(this.floweryTTSConfig.getSilence());
			floweryTTSSourceManager.setSpeed(this.floweryTTSConfig.getSpeed());
			manager.registerSourceManager(floweryTTSSourceManager);
		}
		return manager;
	}

}
