package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topi314.lavasrc.search.SearchManager;
import com.github.topi314.lavasrc.search.SearchManagerConfiguration;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.yandexmusic.YandexMusicSourceManager;
import com.github.topi314.lavasrc.youtube.YoutubeSearchManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LavaSrcPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LavaSrcPlugin.class);

	private SpotifySourceManager spotify;
	private AppleMusicSourceManager appleMusic;
	private DeezerAudioSourceManager deezer;
	private YandexMusicSourceManager yandexMusic;
	private YoutubeSearchManager youtube;

	public LavaSrcPlugin(AudioPlayerManager manager, LavaSrcConfig pluginConfig, SourcesConfig sourcesConfig, SpotifyConfig spotifyConfig, AppleMusicConfig appleMusicConfig, DeezerConfig deezerConfig, YandexMusicConfig yandexMusicConfig) {
		log.info("Loading LavaSrc plugin...");

		if (sourcesConfig.isSpotify()) {
			log.info("Registering Spotify audio source manager...");
			this.spotify = new SpotifySourceManager(pluginConfig.getProviders(), spotifyConfig.getClientId(), spotifyConfig.getClientSecret(), spotifyConfig.getCountryCode(), manager);
			if (spotifyConfig.getPlaylistLoadLimit() > 0) {
				this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
			}
			if (spotifyConfig.getAlbumLoadLimit() > 0) {
				this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
			}
		}
		if (sourcesConfig.isAppleMusic()) {
			log.info("Registering Apple Music audio source manager...");
			var appleMusicSourceManager = new AppleMusicSourceManager(pluginConfig.getProviders(), appleMusicConfig.getMediaAPIToken(), appleMusicConfig.getCountryCode(), manager);
			if (appleMusicConfig.getPlaylistLoadLimit() > 0) {
				appleMusicSourceManager.setPlaylistPageLimit(appleMusicConfig.getPlaylistLoadLimit());
			}
			if (appleMusicConfig.getAlbumLoadLimit() > 0) {
				appleMusicSourceManager.setAlbumPageLimit(appleMusicConfig.getAlbumLoadLimit());
			}
		}
		if (sourcesConfig.isDeezer()) {
			log.info("Registering Deezer audio source manager...");
			this.deezer = new DeezerAudioSourceManager(deezerConfig.getMasterDecryptionKey());
		}
		if (sourcesConfig.isYandexMusic()) {
			log.info("Registering Yandex Music audio source manager...");
			this.yandexMusic = new YandexMusicSourceManager(yandexMusicConfig.getAccessToken());
		}
		if (sourcesConfig.isYoutube()) {
			log.info("Registering Youtube search manager...");
			this.youtube = new YoutubeSearchManager();
		}
	}

	@NotNull
	@Override
	public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
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
		return manager;
	}

	@Override
	@NotNull
	public SearchManager configure(@NotNull SearchManager manager) {
//		if (this.spotify != null) {
//			manager.registerSourceManager(this.spotify);
//		}
//		if (this.appleMusic != null) {
//			manager.registerSourceManager(this.appleMusic);
//		}
		if (this.deezer != null) {
			manager.registerSourceManager(this.deezer);
		}
//		if (this.yandexMusic != null) {
//			manager.registerSourceManager(this.yandexMusic);
//		}
		if (this.youtube != null) {
			manager.registerSourceManager(this.youtube);
		}
		return manager;
	}

}
