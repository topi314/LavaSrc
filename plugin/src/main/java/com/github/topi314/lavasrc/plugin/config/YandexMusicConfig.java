package com.github.topi314.lavasrc.plugin.config;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.yandexmusic")
@Component
public class YandexMusicConfig {

	@Nullable
	private HttpProxyConfig proxy;

	private String accessToken;
	private int playlistLoadLimit = 1;
	private int albumLoadLimit = 1;
	private int artistLoadLimit = 1;

	@Nullable
	public HttpProxyConfig getProxy() {
		return this.proxy;
	}

	@SuppressWarnings("unused")
	public void setProxy(@Nullable HttpProxyConfig proxy) {
		this.proxy = proxy;
	}

	public String getAccessToken() {
		return this.accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public int getPlaylistLoadLimit() {
		return this.playlistLoadLimit;
	}

	public void setPlaylistLoadLimit(int playlistLoadLimit) {
		this.playlistLoadLimit = playlistLoadLimit;
	}

	public int getAlbumLoadLimit() {
		return this.albumLoadLimit;
	}

	public void setAlbumLoadLimit(int albumLoadLimit) {
		this.albumLoadLimit = albumLoadLimit;
	}

	public int getArtistLoadLimit() {
		return this.artistLoadLimit;
	}

	public void setArtistLoadLimit(int artistLoadLimit) {
		this.artistLoadLimit = artistLoadLimit;
	}
}
