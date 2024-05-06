package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.spotify")
@Component
public class SpotifyConfig {

	private String clientId;
	private String clientSecret;
	private String spDc;
	private String countryCode;
	private int playlistLoadLimit;
	private int albumLoadLimit;

	public String getClientId() {
		return this.clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return this.clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getSpDc() {
		return this.spDc;
	}

	public void setSpDc(String spDc) {
		this.spDc = spDc;
	}

	public String getCountryCode() {
		return this.countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
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

}
