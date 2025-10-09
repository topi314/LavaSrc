package com.github.topi314.lavasrc.plugin.config;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.vkmusic")
@Component
public class VkMusicConfig {

	@Nullable
	private HttpProxyConfig proxy;

	private String userToken;
	private int playlistLoadLimit = 1;
	private int artistLoadLimit = 1;
	private int recommendationLoadLimit = 1;

	@Nullable
	public HttpProxyConfig getProxy() {
		return this.proxy;
	}

	@SuppressWarnings("unused")
	public void setProxy(@Nullable HttpProxyConfig proxy) {
		this.proxy = proxy;
	}

	public String getUserToken() {
		return this.userToken;
	}

	public void setUserToken(String userToken) {
		this.userToken = userToken;
	}

	public int getPlaylistLoadLimit() {
		return this.playlistLoadLimit;
	}

	public void setPlaylistLoadLimit(int playlistLoadLimit) {
		this.playlistLoadLimit = playlistLoadLimit;
	}

	public int getArtistLoadLimit() {
		return this.artistLoadLimit;
	}

	public void setArtistLoadLimit(int artistLoadLimit) {
		this.artistLoadLimit = artistLoadLimit;
	}

	public int getRecommendationLoadLimit() {
		return this.recommendationLoadLimit;
	}

	public void setRecommendationLoadLimit(int recommendationLoadLimit) {
		this.recommendationLoadLimit = recommendationLoadLimit;
	}
}