package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavalyrics.LyricsManager;
import com.github.topi314.lavalyrics.api.LyricsManagerConfiguration;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.github.topi314.lavasrc.mirror.DefaultMirrorResolver;
import com.github.topi314.lavasrc.plugin.config.*;
import com.github.topi314.lavasrc.source.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.source.deezer.DeezerAudioSourceManager;
import com.github.topi314.lavasrc.source.deezer.DeezerAudioTrack;
import com.github.topi314.lavasrc.source.flowerytts.FloweryTTSSourceManager;
import com.github.topi314.lavasrc.source.qobuz.QobuzAudioSourceManager;
import com.github.topi314.lavasrc.source.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.source.tidal.TidalSourceManager;
import com.github.topi314.lavasrc.source.vkmusic.VkMusicSourceManager;
import com.github.topi314.lavasrc.source.yandexmusic.YandexMusicSourceManager;
import com.github.topi314.lavasrc.source.youtube.YoutubeSearchManager;
import com.github.topi314.lavasrc.source.ytdlp.YtdlpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Service
@RestController
public class LavaSrcPlugin implements AudioPlayerManagerConfiguration, SearchManagerConfiguration, LyricsManagerConfiguration {

	private static final Logger log = LoggerFactory.getLogger(LavaSrcPlugin.class);

	private AudioPlayerManager playerManager;

	private SpotifySourceManager spotify;
	private SpotifyConfig spotifyConfig;

	private AppleMusicSourceManager appleMusic;
	private AppleMusicConfig appleMusicConfig;

	private DeezerAudioSourceManager deezer;
	private DeezerConfig deezerConfig;

	private YandexMusicSourceManager yandexMusic;
	private YandexMusicConfig yandexMusicConfig;
	private FloweryTTSSourceManager flowerytts;
	private FloweryTTSConfig floweryTTSConfig;

	private YoutubeSearchManager youtube;
	private YouTubeConfig youTubeConfig;

	private VkMusicSourceManager vkMusic;
	private VkMusicConfig vkMusicConfig;

	private TidalSourceManager tidal;
	private TidalConfig tidalConfig;

	private QobuzAudioSourceManager qobuz;
	private QobuzConfig qobuzConfig;

	private YtdlpAudioSourceManager ytdlp;
	private YtdlpConfig ytdlpConfig;

	public LavaSrcPlugin(
		LavaSrcConfig config,
		SpotifyConfig spotifyConfig,
		AppleMusicConfig appleMusicConfig,
		DeezerConfig deezerConfig,
		YandexMusicConfig yandexMusicConfig,
		FloweryTTSConfig floweryTTSConfig,
		YouTubeConfig youTubeConfig,
		VkMusicConfig vkMusicConfig,
		TidalConfig tidalConfig,
		QobuzConfig qobuzConfig,
		YtdlpConfig ytdlpConfig
	) {
		log.info("Loading LavaSrc plugin...");

		var mirrorResolver = new DefaultMirrorResolver(this.playerManager, config.getResolvers());

		this.spotifyConfig = spotifyConfig;
		if (spotifyConfig.isEnabled() || spotifyConfig.isLavaLyricsEnabled() || spotifyConfig.isLavaSearchEnabled()) {
			this.spotify = new SpotifySourceManager(spotifyConfig.getClientId(), spotifyConfig.getClientSecret(), spotifyConfig.getSpDc(), mirrorResolver);
			if (spotifyConfig.getCountryCode() != null) {
				this.spotify.setCountryCode(spotifyConfig.getCountryCode());
			}
			this.spotify.setPreferAnonymousToken(spotifyConfig.isPreferAnonymousToken());
			if (spotifyConfig.getPlaylistLoadLimit() > 0) {
				this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
			}
			if (spotifyConfig.getAlbumLoadLimit() > 0) {
				this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
			}
			this.spotify.setResolveArtistsInSearch(spotifyConfig.isResolveArtistsInSearch());
			this.spotify.setLocalFiles(spotifyConfig.isLocalFiles());
		}

		this.appleMusicConfig = appleMusicConfig;
		if (appleMusicConfig.isEnabled() || appleMusicConfig.isLavaSearchEnabled()) {
			this.appleMusic = new AppleMusicSourceManager(pluginConfig.getProviders(), appleMusicConfig.getMediaAPIToken(), appleMusicConfig.getCountryCode(), unused -> manager);
			if (appleMusicConfig.getPlaylistLoadLimit() > 0) {
				appleMusic.setPlaylistPageLimit(appleMusicConfig.getPlaylistLoadLimit());
			}
			if (appleMusicConfig.getAlbumLoadLimit() > 0) {
				appleMusic.setAlbumPageLimit(appleMusicConfig.getAlbumLoadLimit());
			}
		}

		this.deezerConfig = deezerConfig;
		if (sourcesConfig.isDeezer() || lyricsSourcesConfig.isDeezer()) {
			this.deezer = new DeezerAudioSourceManager(deezerConfig.getMasterDecryptionKey(), deezerConfig.getArl(), deezerConfig.getFormats());
		}

		this.yandexMusicConfig = yandexMusicConfig;
		if (sourcesConfig.isYandexMusic() || lyricsSourcesConfig.isYandexMusic()) {
			this.yandexMusic = new YandexMusicSourceManager(yandexMusicConfig.getAccessToken());
			if (yandexMusicConfig.getPlaylistLoadLimit() > 0) {
				yandexMusic.setPlaylistLoadLimit(yandexMusicConfig.getPlaylistLoadLimit());
			}
			if (yandexMusicConfig.getAlbumLoadLimit() > 0) {
				yandexMusic.setAlbumLoadLimit(yandexMusicConfig.getAlbumLoadLimit());
			}
			if (yandexMusicConfig.getArtistLoadLimit() > 0) {
				yandexMusic.setArtistLoadLimit(yandexMusicConfig.getArtistLoadLimit());
			}
		}

		this.floweryTTSConfig = floweryTTSConfig;
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

		this.youTubeConfig = youTubeConfig;
		if (sourcesConfig.isYoutube() || lyricsSourcesConfig.isYoutube()) {
			if (hasNewYoutubeSource()) {
				log.info("Registering Youtube Source audio source manager...");
				this.youtube = new YoutubeSearchManager(() -> manager, youTubeConfig.getCountryCode(), youTubeConfig.getLanguage());
			} else {
				throw new IllegalStateException("Youtube LavaSearch requires the new Youtube Source plugin to be enabled.");
			}
		}

		this.vkMusicConfig = vkMusicConfig;
		if (sourcesConfig.isVkMusic() || lyricsSourcesConfig.isVkMusic()) {
			this.vkMusic = new VkMusicSourceManager(vkMusicConfig.getUserToken());
			if (vkMusicConfig.getPlaylistLoadLimit() > 0) {
				vkMusic.setPlaylistLoadLimit(vkMusicConfig.getPlaylistLoadLimit());
			}
			if (vkMusicConfig.getArtistLoadLimit() > 0) {
				vkMusic.setArtistLoadLimit(vkMusicConfig.getArtistLoadLimit());
			}
			if (vkMusicConfig.getRecommendationLoadLimit() > 0) {
				vkMusic.setRecommendationsLoadLimit(vkMusicConfig.getRecommendationLoadLimit());
			}
		}

		this.tidalConfig = tidalConfig;
		if (sourcesConfig.isTidal()) {
			this.tidal = new TidalSourceManager(pluginConfig.getProviders(), tidalConfig.getCountryCode(), unused -> this.manager, tidalConfig.getToken());
			if (tidalConfig.getSearchLimit() > 0) {
				this.tidal.setSearchLimit(tidalConfig.getSearchLimit());
			}
		}

		this.qobuzConfig = qobuzConfig;
		if (sourcesConfig.isQobuz()) {
			this.qobuz = new QobuzAudioSourceManager(qobuzConfig.getUserOauthToken(), qobuzConfig.getAppId(), qobuzConfig.getAppSecret());
		}

		this.deezerConfig = deezerConfig;
		if (sourcesConfig.isYtdlp()) {
			this.ytdlp = new YtdlpAudioSourceManager(ytdlpConfig.getPath(), ytdlpConfig.getSearchLimit(), ytdlpConfig.getCustomLoadArgs(), ytdlpConfig.getCustomPlaybackArgs());
		}

		this.ytdlpConfig = ytdlpConfig;
		if (ytdlpConfig.isEnabled()) {
			this.ytdlp = new YtdlpAudioSourceManager(ytdlpConfig.getPath(), ytdlpConfig.getSearchLimit(), ytdlpConfig.getCustomLoadArgs(), ytdlpConfig.getCustomPlaybackArgs());
		}
	}

	private boolean hasNewYoutubeSource() {
		try {
			Class.forName("dev.lavalink.youtube.YoutubeAudioSourceManager");
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	@NotNull
	@Override
	public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
		this.playerManager = manager;

		if (this.spotifyConfig.isEnabled()) {
			log.info("Registering Spotify audio source manager...");
			manager.registerSourceManager(this.spotify);
		}
		if (this.appleMusicConfig.isEnabled()) {
			log.info("Registering Apple Music audio source manager...");
			manager.registerSourceManager(this.appleMusic);
		}
		if (this.deezerConfig.isEnabled()) {
			log.info("Registering Deezer audio source manager...");
			manager.registerSourceManager(this.deezer);
		}
		if (this.yandexMusicConfig.isEnabled()) {
			log.info("Registering Yandex Music audio source manager...");
			manager.registerSourceManager(this.yandexMusic);
		}
		if (this.floweryTTSConfig.isEnabled()) {
			log.info("Registering Flowery TTS audio source manager...");
			manager.registerSourceManager(this.flowerytts);
		}
		if (this.vkMusicConfig.isEnabled()) {
			log.info("Registering Vk Music audio source manager...");
			manager.registerSourceManager(this.vkMusic);
		}
		if (this.tidalConfig.isEnabled()) {
			log.info("Registering Tidal audio source manager...");
			manager.registerSourceManager(this.tidal);
		}
		if (this.qobuzConfig.isEnabled()) {
			log.info("Registering Qobuz audio source manager...");
			manager.registerSourceManager(this.qobuz);
		}
		if (this.ytdlpConfig.isEnabled()) {
			log.info("Registering YTDLP audio source manager...");
			manager.registerSourceManager(this.ytdlp);
		}
		return manager;
	}

	@Override
	@NotNull
	public SearchManager configure(@NotNull SearchManager manager) {
		if (this.spotifyConfig.isLavaSearchEnabled()) {
			log.info("Registering Spotify search manager...");
			manager.registerSearchManager(this.spotify);
		}
		if (this.appleMusicConfig.isLavaSearchEnabled()) {
			log.info("Registering Apple Music search manager...");
			manager.registerSearchManager(this.appleMusic);
		}
		if (this.deezerConfig.isLavaSearchEnabled()) {
			log.info("Registering Deezer search manager...");
			manager.registerSearchManager(this.deezer);
		}
		if (this.youTubeConfig.isLavaSearchEnabled()) {
			log.info("Registering Youtube search manager...");
			manager.registerSearchManager(this.youtube);
		}
		if (this.yandexMusicConfig.isLavaSearchEnabled()) {
			log.info("Registering Yandex Music search manager...");
			manager.registerSearchManager(this.yandexMusic);
		}
		if (this.vkMusicConfig.isLavaSearchEnabled()) {
			log.info("Registering VK Music search manager...");
			manager.registerSearchManager(this.vkMusic);
		}
		return manager;
	}

	@NotNull
	@Override
	public LyricsManager configure(@NotNull LyricsManager manager) {
		if (this.spotifyConfig.isLavaLyricsEnabled()) {
			log.info("Registering Spotify lyrics manager...");
			manager.registerLyricsManager(this.spotify);
		}
		if (this.deezerConfig.isLavaLyricsEnabled()) {
			log.info("Registering Deezer lyrics manager...");
			manager.registerLyricsManager(this.deezer);
		}
		if (this.youTubeConfig.isLavaLyricsEnabled()) {
			log.info("Registering YouTube lyrics manager...");
			manager.registerLyricsManager(this.youtube);
		}
		if (this.yandexMusicConfig.isLavaLyricsEnabled()) {
			log.info("Registering Yandex Music lyrics manager");
			manager.registerLyricsManager(this.yandexMusic);
		}
		if (this.vkMusicConfig.isLavaLyricsEnabled()) {
			log.info("Registering VK Music lyrics manager...");
			manager.registerLyricsManager(this.vkMusic);
		}
		return manager;
	}

	@PatchMapping("/v4/lavasrc/config")
	public void updateConfig(@RequestBody Config config) {
		var spotifyConfig = config.getSpotify();
		if (spotifyConfig != null && this.spotify != null) {
			if (spotifyConfig.getSpDc() != null) {
				this.spotify.setSpDc(spotifyConfig.getSpDc());
			}
			if (spotifyConfig.getClientId() != null && spotifyConfig.getClientSecret() != null) {
				this.spotify.setClientIDSecret(spotifyConfig.getClientId(), spotifyConfig.getClientSecret());
			}
			if (spotifyConfig.getPreferAnonymousToken() != null) {
				this.spotify.setPreferAnonymousToken(spotifyConfig.getPreferAnonymousToken());
			}
		}

		var appleMusicConfig = config.getAppleMusic();
		if (appleMusicConfig != null && this.appleMusic != null && appleMusicConfig.getMediaAPIToken() != null) {
			this.appleMusic.setMediaAPIToken(appleMusicConfig.getMediaAPIToken());
		}

		var deezerConfig = config.getDeezer();
		if (deezerConfig != null && this.deezer != null) {
			if (deezerConfig.getArl() != null) {
				this.deezer.setArl(deezerConfig.getArl());
			}
			if (deezerConfig.getFormats() != null) {
				this.deezer.setFormats(deezerConfig.getFormats()
					.stream()
					.map(deezerTrackFormat -> DeezerAudioTrack.TrackFormat.from(deezerTrackFormat.name()))
					.toList()
					.toArray(new DeezerAudioTrack.TrackFormat[0]));
			}
		}

		var yandexMusicConfig = config.getYandexMusic();
		if (yandexMusicConfig != null && this.yandexMusic != null) {
			if (yandexMusicConfig.getAccessToken() != null) {
				this.yandexMusic.setAccessToken(yandexMusicConfig.getAccessToken());
			}
		}

		var vkMusicConfig = config.getVkMusic();
		if (vkMusicConfig != null && this.vkMusic != null) {
			if (vkMusicConfig.getUserToken() != null) {
				this.vkMusic.setUserToken(vkMusicConfig.getUserToken());
			}
		}

		var qobuzConfig = config.getQobuz();
		if (qobuzConfig != null && this.qobuz != null) {
			if (qobuzConfig.getUserOauthToken() != null) {
				this.qobuz.setUserOauthToken(qobuzConfig.getUserOauthToken());
			}
			if (qobuzConfig.getAppId() != null && qobuzConfig.getAppSecret() != null) {
				this.qobuz.setAppId(qobuzConfig.getAppId());
				this.qobuz.setAppSecret(qobuzConfig.getAppSecret());
			}
		}

		var ytdlpConfig = config.getYtdlp();
		if (ytdlpConfig != null && this.ytdlp != null) {
			if (ytdlpConfig.getPath() != null) {
				this.ytdlp.setPath(ytdlpConfig.getPath());
			}
			if (ytdlpConfig.getSearchLimit() > 0) {
				this.ytdlp.setSearchLimit(ytdlpConfig.getSearchLimit());
			}
			if (ytdlpConfig.getCustomLoadArgs() != null) {
				this.ytdlp.setCustomLoadArgs(ytdlpConfig.getCustomLoadArgs().toArray(String[]::new));
			}
			if (ytdlpConfig.getCustomPlaybackArgs() != null) {
				this.ytdlp.setCustomPlaybackArgs(ytdlpConfig.getCustomPlaybackArgs().toArray(String[]::new));
			}
		}
	}
}
