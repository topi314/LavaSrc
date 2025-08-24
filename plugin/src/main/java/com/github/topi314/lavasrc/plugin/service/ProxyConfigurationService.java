package com.github.topi314.lavasrc.plugin.service;

import com.github.topi314.lavasrc.plugin.config.HttpProxyConfig;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProxyConfigurationService {

	private static final Logger log = LoggerFactory.getLogger(ProxyConfigurationService.class);

	public void configure(HttpConfigurable httpConfigurable, HttpProxyConfig proxyConfig) {
		if (proxyConfig == null || proxyConfig.getUrl() == null) {
			return;
		}

		HttpHost httpHost = HttpHost.create(proxyConfig.getUrl());
		BasicCredentialsProvider credentialsProvider = createCredentialsProvider(proxyConfig, httpHost);

		log.info("Configuring HTTP proxy {} with authentication: {}", httpHost, credentialsProvider != null);

		httpConfigurable.configureBuilder(builder -> {

			builder.setProxy(httpHost);

			if (credentialsProvider != null) {
				builder.setDefaultCredentialsProvider(credentialsProvider);
			}

		});
	}

	protected BasicCredentialsProvider createCredentialsProvider(HttpProxyConfig proxyConfig, HttpHost httpHost) {
		String username = proxyConfig.getUsername();
		String password = proxyConfig.getPassword();

		if (username == null || password == null) {
			return null;
		}

		BasicCredentialsProvider provider = new BasicCredentialsProvider();

		provider.setCredentials(
			new AuthScope(httpHost),
			new UsernamePasswordCredentials(username, password)
		);

		return provider;
	}

}
