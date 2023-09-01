package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.yandexmusic.YandexMusicSourceManager;
import com.github.topi314.lavasrc.youtube.YoutubeSearchManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LavaSrcPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LavaSrcPlugin.class);

	private AudioPlayerManager manager;
	private YoutubeAudioSourceManager youtubeAudioSourceManager;
	private SpotifySourceManager spotify;
	private AppleMusicSourceManager appleMusic;
	private DeezerAudioSourceManager deezer;
	private YandexMusicSourceManager yandexMusic;
	private FloweryTTSSourceManager flowerytts;
	private YoutubeSearchManager youtube;

	public LavaSrcPlugin(LavaSrcConfig pluginConfig, SourcesConfig sourcesConfig, SpotifyConfig spotifyConfig, AppleMusicConfig appleMusicConfig, DeezerConfig deezerConfig, YandexMusicConfig yandexMusicConfig, FloweryTTSConfig floweryTTSConfig) {
		log.info("Loading LavaSrc plugin...");

		if (sourcesConfig.isSpotify()) {
			log.info("Registering Spotify audio source manager...");
			this.spotify = new SpotifySourceManager(pluginConfig.getProviders(), spotifyConfig.getClientId(), spotifyConfig.getClientSecret(), spotifyConfig.getCountryCode(), unused -> manager);
			if (spotifyConfig.getPlaylistLoadLimit() > 0) {
				this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
			}
			if (spotifyConfig.getAlbumLoadLimit() > 0) {
				this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
			}
		}
		if (sourcesConfig.isAppleMusic()) {
			log.info("Registering Apple Music audio source manager...");
			var appleMusicSourceManager = new AppleMusicSourceManager(pluginConfig.getProviders(), appleMusicConfig.getMediaAPIToken(), appleMusicConfig.getCountryCode(), unused -> manager);
			if (appleMusicConfig.getPlaylistLoadLimit() > 0) {
				appleMusicSourceManager.setPlaylistPageLimit(appleMusicConfig.getPlaylistLoadLimit());
			}
			if (appleMusicConfig.getAlbumLoadLimit() > 0) {
				appleMusicSourceManager.setAlbumPageLimit(appleMusicConfig.getAlbumLoadLimit());
			}
			this.appleMusic = appleMusicSourceManager;
		}
		if (sourcesConfig.isDeezer()) {
			log.info("Registering Deezer audio source manager...");
			this.deezer = new DeezerAudioSourceManager(deezerConfig.getMasterDecryptionKey());
		}
		if (sourcesConfig.isYandexMusic()) {
			log.info("Registering Yandex Music audio source manager...");
			this.yandexMusic = new YandexMusicSourceManager(yandexMusicConfig.getAccessToken());
		}
		if (sourcesConfig.isFloweryTTS()) {
			log.info("Registering Flowery TTS audio source manager...");
			this.flowerytts = new FloweryTTSSourceManager(floweryTTSConfig.getVoice());
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
		if (sourcesConfig.isYoutube()) {
			log.info("Registering Youtube search manager...");
			this.youtube = new YoutubeSearchManager(() -> youtubeAudioSourceManager);
		}
	}

	@NotNull
	@Override
	public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
		this.manager = manager;
		if (this.spotify != null) {
			manager.registerSourceManager(this.spotify);
		}
		if (this.appleMusic != null) {
			manager.registerSourceManager(this.appleMusic);
		}
		if (this.deezer != null) {
			manager.registerSourceManager(this.deezer);
		}
		if (this.yandexMusic != null) {
			manager.registerSourceManager(this.yandexMusic);
		}
		if (this.flowerytts != null) {
			manager.registerSourceManager(this.flowerytts);
		}
		if (this.youtube != null) {
			this.youtubeAudioSourceManager = manager.source(YoutubeAudioSourceManager.class);
		}
		return manager;
	}

	@Override
	@NotNull
	public AudioSearchManager configure(@NotNull AudioSearchManager manager) {
		if (this.spotify != null) {
			manager.registerSourceManager(this.spotify);
		}
		if (this.appleMusic != null) {
			manager.registerSourceManager(this.appleMusic);
		}
		if (this.deezer != null) {
			manager.registerSourceManager(this.deezer);
		}
		if (this.youtube != null) {
			if (this.youtubeAudioSourceManager == null) {
				throw new IllegalStateException("Youtube audio source manager is not initialized but required by Youtube search manager.");
			}
			manager.registerSourceManager(this.youtube);
		}
		return manager;
	}

}
