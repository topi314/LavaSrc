package com.github.topi314.lavasrc.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.youtube")
@Component
public class YouTubeConfig {

	private boolean lavaLyricsEnabled = false;
	private boolean lavaSearchEnabled = false;
	private String countryCode = "US";
	private String language = "en";

	public boolean isLavaLyricsEnabled() {
		return lavaLyricsEnabled;
	}

	public void setLavaLyricsEnabled(boolean lavaLyricsEnabled) {
		this.lavaLyricsEnabled = lavaLyricsEnabled;
	}

	public boolean isLavaSearchEnabled() {
		return lavaSearchEnabled;
	}

	public void setLavaSearchEnabled(boolean lavaSearchEnabled) {
		this.lavaSearchEnabled = lavaSearchEnabled;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

}
