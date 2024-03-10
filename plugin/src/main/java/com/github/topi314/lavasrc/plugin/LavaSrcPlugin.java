package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavalyrics.LyricsManager;
import com.github.topi314.lavalyrics.api.LyricsManagerConfiguration;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.yandexmusic.YandexMusicSourceManager;
import com.github.topi314.lavasrc.youtube.YoutubeSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LavaSrcPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration, LyricsManagerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LavaSrcPlugin.class);

	private AudioPlayerManager manager;
	private YoutubeAudioSourceManager youtubeAudioSourceManager;
	private SpotifySourceManager spotify;
	private AppleMusicSourceManager appleMusic;
	private DeezerAudioSourceManager deezer;
	private YandexMusicSourceManager yandexMusic;
	private FloweryTTSSourceManager flowerytts;
	private YoutubeSourceManager youtube;

	public LavaSrcPlugin(LavaSrcConfig pluginConfig, SourcesConfig sourcesConfig, SpotifyConfig spotifyConfig, AppleMusicConfig appleMusicConfig, DeezerConfig deezerConfig, YandexMusicConfig yandexMusicConfig, FloweryTTSConfig floweryTTSConfig, YouTubeConfig youTubeConfig) {
		log.info("Loading LavaSrc plugin...");

		if (sourcesConfig.isSpotify()) {
			this.spotify = new SpotifySourceManager(spotifyConfig.getClientId(), spotifyConfig.getClientSecret(), spotifyConfig.getSpDc(), spotifyConfig.getCountryCode(), unused -> manager, new DefaultMirroringAudioTrackResolver(pluginConfig.getProviders()));
			if (spotifyConfig.getPlaylistLoadLimit() > 0) {
				this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
			}
			if (spotifyConfig.getAlbumLoadLimit() > 0) {
				this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
			}
		}
		if (sourcesConfig.isAppleMusic()) {
			this.appleMusic = new AppleMusicSourceManager(pluginConfig.getProviders(), appleMusicConfig.getMediaAPIToken(), appleMusicConfig.getCountryCode(), unused -> manager);
			if (appleMusicConfig.getPlaylistLoadLimit() > 0) {
				appleMusic.setPlaylistPageLimit(appleMusicConfig.getPlaylistLoadLimit());
			}
			if (appleMusicConfig.getAlbumLoadLimit() > 0) {
				appleMusic.setAlbumPageLimit(appleMusicConfig.getAlbumLoadLimit());
			}
		}
		if (sourcesConfig.isDeezer()) {
			this.deezer = new DeezerAudioSourceManager(deezerConfig.getMasterDecryptionKey());
		}
		if (sourcesConfig.isYandexMusic()) {
			this.yandexMusic = new YandexMusicSourceManager(yandexMusicConfig.getAccessToken());
		}
		if (sourcesConfig.isFloweryTTS()) {
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
			this.youtube = new YoutubeSourceManager(() -> youtubeAudioSourceManager, youTubeConfig.getCountryCode());
		}
	}

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
		if (this.yandexMusic != null) {
			log.info("Registering Yandex Music audio source manager...");
			manager.registerSourceManager(this.yandexMusic);
		}
		if (this.flowerytts != null) {
			log.info("Registering Flowery TTS audio source manager...");
			manager.registerSourceManager(this.flowerytts);
		}
		if (this.youtube != null) {
			this.youtubeAudioSourceManager = manager.source(YoutubeAudioSourceManager.class);
			if (this.youtubeAudioSourceManager == null) {
				throw new IllegalStateException("Youtube LavaSearch requires YoutubeAudioSourceManager to be enabled.");
			}

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
		if (this.youtube != null) {
			log.info("Registering Youtube search manager...");
			manager.registerSearchManager(this.youtube);
		}
		return manager;
	}

	@NotNull
	@Override
	public LyricsManager configure(@NotNull LyricsManager manager) {
		if (this.spotify != null) {
			log.info("Registering Spotify lyrics manager...");
			manager.registerLyricsManager(this.spotify);
		}
		if (this.deezer != null) {
			log.info("Registering Deezer lyrics manager...");
			manager.registerLyricsManager(this.deezer);
		}
		if (this.youtube != null) {
			log.info("Registering YouTube lyrics manager...");
			manager.registerLyricsManager(this.youtube);
		}
		return manager;
	}
}
