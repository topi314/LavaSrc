package com.github.topi314.lavasrc.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.ytdlp")
@Component
public class YtdlpConfig {

	private String path;
	private int searchLimit = 10;
	private int mixPlaylistLoadLimit = 25;
	private int playlistLoadLimit = 1000;
	private String[] customLoadArgs;
	private String[] customPlaybackArgs;

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getSearchLimit() {
		return searchLimit;
	}

	public void setSearchLimit(int searchLimit) {
		this.searchLimit = searchLimit;
	}

	public int getMixPlaylistLoadLimit() {
		return mixPlaylistLoadLimit;
	}

	public void setMixPlaylistLoadLimit(int mixPlaylistLoadLimit) {
		this.mixPlaylistLoadLimit = mixPlaylistLoadLimit;
	}

	public int getPlaylistLoadLimit() {
		return playlistLoadLimit;
	}

	public void setPlaylistLoadLimit(int playlistLoadLimit) {
		this.playlistLoadLimit = playlistLoadLimit;
	}

	public String[] getCustomLoadArgs() {
		return customLoadArgs;
	}

	public void setCustomLoadArgs(String[] customLoadArgs) {
		this.customLoadArgs = customLoadArgs;
	}

	public String[] getCustomPlaybackArgs() {
		return customPlaybackArgs;
	}

	public void setCustomPlaybackArgs(String[] customPlaybackArgs) {
		this.customPlaybackArgs = customPlaybackArgs;
	}

}
