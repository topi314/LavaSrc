package com.github.topi314.lavasrc.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "plugins.lavasrc.spotify")
@Component
public class SpotifyConfig {

	private boolean enabled = false;
	private boolean lavaLyricsEnabled = false;
	private boolean lavaSearchEnabled = false;
	private String clientId;
	private String clientSecret;
	private String spDc;
	private String countryCode = "US";
	private int playlistLoadLimit = 6;
	private int albumLoadLimit = 6;
	private boolean resolveArtistsInSearch = true;
	private boolean localFiles = false;
	private boolean preferAnonymousToken = false;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isLavaLyricsEnabled() {
		return this.lavaLyricsEnabled;
	}

	public void setLavaLyricsEnabled(boolean lavaLyricsEnabled) {
		this.lavaLyricsEnabled = lavaLyricsEnabled;
	}

	public boolean isLavaSearchEnabled() {
		return this.lavaSearchEnabled;
	}

	public void setLavaSearchEnabled(boolean lavaSearchEnabled) {
		this.lavaSearchEnabled = lavaSearchEnabled;
	}

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

	public boolean isResolveArtistsInSearch() {
		return this.resolveArtistsInSearch;
	}

	public void setResolveArtistsInSearch(boolean resolveArtistsInSearch) {
		this.resolveArtistsInSearch = resolveArtistsInSearch;
	}

	public boolean isLocalFiles() {
		return this.localFiles;
	}

	public void setLocalFiles(boolean localFiles) {
		this.localFiles = localFiles;
	}

	public boolean isPreferAnonymousToken() {
		return this.preferAnonymousToken;
	}

	public void setPreferAnonymousToken(boolean preferAnonymousToken) {
		this.preferAnonymousToken = preferAnonymousToken;
	}
}
