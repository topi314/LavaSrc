package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.proxy.ProxyConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(
	prefix = "plugins.lavasrc.saavn"
)
@Component
public class SaavnConfig {
	private ProxyConfig[] proxyConfig;
	private String apiUrl;

	public ProxyConfig[] getProxyConfig() {
		return this.proxyConfig;
	}

	public void setProxyConfig(ProxyConfig[] proxyConfig) {
		this.proxyConfig = proxyConfig;
	}

	public String getApiUrl() {
		return this.apiUrl;
	}

	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}
}
