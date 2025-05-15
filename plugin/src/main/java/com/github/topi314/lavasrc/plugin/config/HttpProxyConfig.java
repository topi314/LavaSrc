package com.github.topi314.lavasrc.plugin.config;

import org.jetbrains.annotations.Nullable;

public class HttpProxyConfig {
	@Nullable
	private String url;
	@Nullable
	private String username;
	@Nullable
	private String password;

	public @Nullable String getUrl() {
		return url;
	}

	@SuppressWarnings("unused")
	public void setUrl(@Nullable String url) {
		this.url = url;
	}

	public @Nullable String getUsername() {
		return username;
	}

	@SuppressWarnings("unused")
	public void setUsername(@Nullable String username) {
		this.username = username;
	}

	public @Nullable String getPassword() {
		return password;
	}

	@SuppressWarnings("unused")
	public void setPassword(@Nullable String password) {
		this.password = password;
	}
}
