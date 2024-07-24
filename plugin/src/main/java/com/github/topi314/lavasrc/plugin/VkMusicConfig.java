package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.vkmusic")
@Component
public class VkMusicConfig {

	private String userToken;

	public String getUserToken() {
		return this.userToken;
	}

	public void setUserToken(String userToken) {
		this.userToken = userToken;
	}
}