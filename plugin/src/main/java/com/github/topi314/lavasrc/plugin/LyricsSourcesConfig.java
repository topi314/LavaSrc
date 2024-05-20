package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@ConfigurationProperties(prefix = "plugins.lavasrc.lyrics-sources")
@Component
public class LyricsSourcesConfig {

	private boolean spotify = false;
	private boolean deezer = false;
	private boolean youtube = false;
	private boolean yandexMusic = false;

	public boolean isSpotify() {
		return this.spotify;
	}

	public void setSpotify(boolean spotify) {
		this.spotify = spotify;
	}

	public boolean isDeezer() {
		return this.deezer;
	}

	public void setDeezer(boolean deezer) {
		this.deezer = deezer;
	}

	public boolean isYoutube() {
		return this.youtube;
	}

	public void setYoutube(boolean youtube) {
		this.youtube = youtube;
	}

	public boolean isYandexMusic() {
		return this.yandexMusic;
	}

	public void setYandexMusic(boolean youtube) {
		this.yandexMusic = youtube;
	}
}
