package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavalyrics.LyricsManager;
import com.github.topi314.lavalyrics.api.LyricsManagerConfiguration;
import com.github.topi314.lavasearch.SearchManager;
import com.github.topi314.lavasearch.api.SearchManagerConfiguration;
import com.github.topi314.lavasrc.applemusic.AppleMusicSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioSourceManager;
import com.github.topi314.lavasrc.deezer.DeezerAudioTrack;
import com.github.topi314.lavasrc.flowerytts.FloweryTTSSourceManager;
import com.github.topi314.lavasrc.jiosaavn.JioSaavnAudioSourceManager;
import com.github.topi314.lavasrc.lrclib.LrcLibLyricsManager;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.plugin.config.*;
import com.github.topi314.lavasrc.plugin.service.ProxyConfigurationService;
import com.github.topi314.lavasrc.protocol.Config;
import com.github.topi314.lavasrc.qobuz.QobuzAudioSourceManager;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.tidal.TidalSourceManager;
import com.github.topi314.lavasrc.pandora.PandoraSourceManager;
import com.github.topi314.lavasrc.vkmusic.VkMusicSourceManager;
import com.github.topi314.lavasrc.yandexmusic.YandexMusicSourceManager;
import com.github.topi314.lavasrc.youtube.YoutubeSearchManager;
import com.github.topi314.lavasrc.ytdlp.YtdlpAudioSourceManager;
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

	private final SourcesConfig sourcesConfig;
	private final LyricsSourcesConfig lyricsSourcesConfig;
	private AudioPlayerManager manager;
	private SpotifySourceManager spotify;
	private AppleMusicSourceManager appleMusic;
	private DeezerAudioSourceManager deezer;
	private YandexMusicSourceManager yandexMusic;
	private FloweryTTSSourceManager flowerytts;
	private YoutubeSearchManager youtube;
	private VkMusicSourceManager vkMusic;
	private TidalSourceManager tidal;
	private JioSaavnAudioSourceManager jioSaavn;
	private QobuzAudioSourceManager qobuz;
	private YtdlpAudioSourceManager ytdlp;
	private LrcLibLyricsManager lrcLib;
	private PandoraSourceManager pandora;

	public LavaSrcPlugin(
		LavaSrcConfig pluginConfig,
		SourcesConfig sourcesConfig,
		LyricsSourcesConfig lyricsSourcesConfig,
		SpotifyConfig spotifyConfig,
		AppleMusicConfig appleMusicConfig,
		DeezerConfig deezerConfig,
		YandexMusicConfig yandexMusicConfig,
		FloweryTTSConfig floweryTTSConfig,
		YouTubeConfig youTubeConfig,
		VkMusicConfig vkMusicConfig,
		TidalConfig tidalConfig,
		QobuzConfig qobuzConfig,
		YtdlpConfig ytdlpConfig,
		JioSaavnConfig jioSaavnConfig,
		PandoraConfig pandoraConfig,
		ProxyConfigurationService proxyConfigurationService
	) {
		log.info("Loading LavaSrc plugin...");
		this.sourcesConfig = sourcesConfig;
		this.lyricsSourcesConfig = lyricsSourcesConfig;

		if (sourcesConfig.isSpotify() || lyricsSourcesConfig.isSpotify()) {
			this.spotify = new SpotifySourceManager(spotifyConfig.getClientId(), spotifyConfig.getClientSecret(), spotifyConfig.isPreferAnonymousToken(), spotifyConfig.getCustomTokenEndpoint(), spotifyConfig.getSpDc(), spotifyConfig.getCountryCode(), unused -> manager, new DefaultMirroringAudioTrackResolver(pluginConfig.getProviders()));
			if (spotifyConfig.getPlaylistLoadLimit() > 0) {
				this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
			}
			if (spotifyConfig.getAlbumLoadLimit() > 0) {
				this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
			}
			if (!spotifyConfig.isResolveArtistsInSearch()) {
				this.spotify.setResolveArtistsInSearch(spotifyConfig.isResolveArtistsInSearch());
			}
			if (spotifyConfig.isLocalFiles()) {
				this.spotify.setLocalFiles(spotifyConfig.isLocalFiles());
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
		if (sourcesConfig.isDeezer() || lyricsSourcesConfig.isDeezer()) {
			this.deezer = new DeezerAudioSourceManager(deezerConfig.getMasterDecryptionKey(), deezerConfig.getArl(), deezerConfig.getFormats());
		}

		if (sourcesConfig.isYandexMusic() || lyricsSourcesConfig.isYandexMusic()) {
			this.yandexMusic = new YandexMusicSourceManager(yandexMusicConfig.getAccessToken());

			proxyConfigurationService.configure(this.yandexMusic, yandexMusicConfig.getProxy());

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
		if (sourcesConfig.isYoutube() || lyricsSourcesConfig.isYoutube()) {
			if (hasNewYoutubeSource()) {
				log.info("Registering Youtube Source audio source manager...");
				this.youtube = new YoutubeSearchManager(() -> manager, youTubeConfig.getCountryCode(), youTubeConfig.getLanguage());
			} else {
				throw new IllegalStateException("Youtube LavaSearch requires the new Youtube Source plugin to be enabled.");
			}
		}
		if (sourcesConfig.isVkMusic() || lyricsSourcesConfig.isVkMusic()) {
			this.vkMusic = new VkMusicSourceManager(vkMusicConfig.getUserToken());
			proxyConfigurationService.configure(this.vkMusic, vkMusicConfig.getProxy());

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
		if (sourcesConfig.isTidal()) {
			this.tidal = new TidalSourceManager(pluginConfig.getProviders(), tidalConfig.getCountryCode(), unused -> this.manager, tidalConfig.getToken());
			if (tidalConfig.getSearchLimit() > 0) {
				this.tidal.setSearchLimit(tidalConfig.getSearchLimit());
			}
		}
		if (sourcesConfig.isQobuz()) {
			this.qobuz = new QobuzAudioSourceManager(qobuzConfig.getUserOauthToken(), qobuzConfig.getAppId(), qobuzConfig.getAppSecret());
		}
		if (sourcesConfig.isYtdlp()) {
			this.ytdlp = new YtdlpAudioSourceManager(ytdlpConfig.getPath(), ytdlpConfig.getSearchLimit(), ytdlpConfig.getCustomLoadArgs(), ytdlpConfig.getCustomPlaybackArgs());
		}

		if (lyricsSourcesConfig.isLrcLib()) {
			this.lrcLib = new LrcLibLyricsManager();
		}

		if (sourcesConfig.isJiosaavn()) {
			this.jioSaavn = new JioSaavnAudioSourceManager(jioSaavnConfig.buildConfig());

			proxyConfigurationService.configure(this.jioSaavn, jioSaavnConfig.getProxy());
		}

		if (sourcesConfig.isPandora()) {
			this.pandora = new PandoraSourceManager(pluginConfig.getProviders(), pandoraConfig.getCookie(), pandoraConfig.getCsrfToken(), pandoraConfig.getAuthToken(), unused -> this.manager);
			if (pandoraConfig.getSearchLimit() > 0) {
				this.pandora.setSearchLimit(pandoraConfig.getSearchLimit());
			}
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
		this.manager = manager;
		if (this.spotify != null && this.sourcesConfig.isSpotify()) {
			log.info("Registering Spotify audio source manager...");
			manager.registerSourceManager(this.spotify);
		}
		if (this.appleMusic != null) {
			log.info("Registering Apple Music audio source manager...");
			manager.registerSourceManager(this.appleMusic);
		}
		if (this.deezer != null && this.sourcesConfig.isDeezer()) {
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
		if (this.vkMusic != null) {
			log.info("Registering Vk Music audio source manager...");
			manager.registerSourceManager(this.vkMusic);
		}
		if (this.tidal != null) {
			log.info("Registering Tidal audio source manager...");
			manager.registerSourceManager(this.tidal);
		}
		if (this.qobuz != null) {
			log.info("Registering Qobuz audio source manager...");
			manager.registerSourceManager(this.qobuz);
		}
		if (this.ytdlp != null) {
			log.info("Registering YTDLP audio source manager...");
			manager.registerSourceManager(this.ytdlp);
		}
		if (this.jioSaavn != null) {
			log.info("Registering JioSaavn audio source manager...");
			manager.registerSourceManager(this.jioSaavn);
		}
		if (this.pandora != null) {
			log.info("Registering Pandora audio source manager...");
			manager.registerSourceManager(this.pandora);
		}
		return manager;
	}

	@Override
	@NotNull
	public SearchManager configure(@NotNull SearchManager manager) {
		if (this.spotify != null && this.sourcesConfig.isSpotify()) {
			log.info("Registering Spotify search manager...");
			manager.registerSearchManager(this.spotify);
		}
		if (this.appleMusic != null && this.sourcesConfig.isAppleMusic()) {
			log.info("Registering Apple Music search manager...");
			manager.registerSearchManager(this.appleMusic);
		}
		if (this.deezer != null && this.sourcesConfig.isDeezer()) {
			log.info("Registering Deezer search manager...");
			manager.registerSearchManager(this.deezer);
		}
		if (this.youtube != null && this.sourcesConfig.isYoutube()) {
			log.info("Registering Youtube search manager...");
			manager.registerSearchManager(this.youtube);
		}
		if (this.yandexMusic != null && this.sourcesConfig.isYandexMusic()) {
			log.info("Registering Yandex Music search manager...");
			manager.registerSearchManager(this.yandexMusic);
		}
		if (this.vkMusic != null && this.sourcesConfig.isVkMusic()) {
			log.info("Registering VK Music search manager...");
			manager.registerSearchManager(this.vkMusic);
		}
		if (this.jioSaavn != null && this.sourcesConfig.isJiosaavn()) {
			log.info("Registering JioSaavn search manager...");
			manager.registerSearchManager(this.jioSaavn);
		}
		if (this.pandora != null && this.sourcesConfig.isPandora()) {
			log.info("Registering Pandora audio search manager...");
			manager.registerSearchManager(this.pandora);
		}
		return manager;
	}

	@NotNull
	@Override
	public LyricsManager configure(@NotNull LyricsManager manager) {
		if (this.spotify != null && this.lyricsSourcesConfig.isSpotify()) {
			log.info("Registering Spotify lyrics manager...");
			manager.registerLyricsManager(this.spotify);
		}
		if (this.deezer != null && this.lyricsSourcesConfig.isDeezer()) {
			log.info("Registering Deezer lyrics manager...");
			manager.registerLyricsManager(this.deezer);
		}
		if (this.youtube != null && this.lyricsSourcesConfig.isYoutube()) {
			log.info("Registering YouTube lyrics manager...");
			manager.registerLyricsManager(this.youtube);
		}
		if (this.yandexMusic != null && this.lyricsSourcesConfig.isYandexMusic()) {
			log.info("Registering Yandex Music lyrics manager");
			manager.registerLyricsManager(this.yandexMusic);
		}
		if (this.vkMusic != null && this.lyricsSourcesConfig.isVkMusic()) {
			log.info("Registering VK Music lyrics manager...");
			manager.registerLyricsManager(this.vkMusic);
		}
		if (this.lrcLib != null && this.lyricsSourcesConfig.isLrcLib()) {
			log.info("Registering LRCLIB lyrics manager...");
			manager.registerLyricsManager(this.lrcLib);
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
			if (spotifyConfig.getCustomTokenEndpoint() != null) {
				this.spotify.setCustomTokenEndpoint(spotifyConfig.getCustomTokenEndpoint());
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

		var pandoraConfig = config.getPandora();
		if (pandoraConfig != null && this.pandora != null) {
			if (pandoraConfig.getCookie() != null) {
				this.pandora.setCookie(pandoraConfig.getCookie());
			}
			if (pandoraConfig.getCsrfToken() != null) {
				this.pandora.setCsrfToken(pandoraConfig.getCsrfToken());
			}
			if (pandoraConfig.getAuthToken() != null) {
				this.pandora.setAuthToken(pandoraConfig.getAuthToken());
			}
		}
	}
}