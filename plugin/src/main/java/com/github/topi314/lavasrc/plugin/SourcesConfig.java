package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@ConfigurationProperties(prefix = "plugins.lavasrc.sources")
@Component
public class SourcesConfig {

	private boolean spotify = false;
	private boolean appleMusic = false;
	private boolean deezer = false;
	private boolean yandexMusic = false;

	public boolean isSpotify() {
		return this.spotify;
	}

	public void setSpotify(boolean spotify) {
		this.spotify = spotify;
	}

	public boolean isAppleMusic() {
		return this.appleMusic;
	}

	public void setAppleMusic(boolean appleMusic) {
		this.appleMusic = appleMusic;
	}

	public boolean isDeezer() {
		return this.deezer;
	}

	public void setDeezer(boolean deezer) {
		this.deezer = deezer;
	}

	public boolean isYandexMusic() {
		return this.yandexMusic;
	}

	public void setYandexMusic(boolean yandexMusic) {
		this.yandexMusic = yandexMusic;
	}
}
