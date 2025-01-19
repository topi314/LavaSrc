package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.proxy.ProxyConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.jiosaavn")
@Component
public class JioSaavnConfig {
	private ProxyConfig[] proxies;
	private String apiUrl;
	private boolean useLocalNetwork;

	public ProxyConfig[] getProxies() {
		return this.proxies;
	}

	public void setProxies(ProxyConfig[] proxies) {
		this.proxies = proxies;
	}

	public String getApiUrl() {
		return this.apiUrl;
	}

	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public boolean isUseLocalNetwork() {
		return useLocalNetwork;
	}

	public void setUseLocalNetwork(boolean useLocalNetwork) {
		this.useLocalNetwork = useLocalNetwork;
	}
}
