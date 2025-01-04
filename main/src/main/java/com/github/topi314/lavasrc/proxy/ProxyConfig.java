package com.github.topi314.lavasrc.proxy;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

public class ProxyConfig {
	private String proxyProtocol = "http";
	private String proxyHost;
	private int proxyPort;
	private String proxyUser;
	private String proxyPassword;

	public String getProxyProtocol() {
		return this.proxyProtocol;
	}

	public void setProxyProtocol(String proxyProtocol) {
		this.proxyProtocol = proxyProtocol;
	}

	public String getProxyHost() {
		return this.proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public int getProxyPort() {
		return this.proxyPort;
	}

	public void setProxyPort(Integer proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String getProxyUser() {
		return this.proxyUser;
	}

	public void setProxyUser(String proxyUser) {
		this.proxyUser = proxyUser;
	}

	public String getProxyPassword() {
		return this.proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		this.proxyPassword = proxyPassword;
	}

	public RequestConfig configureRequest(RequestConfig requestConfig) {
		var proxy = new HttpHost(this.getProxyHost(), this.getProxyPort(), this.getProxyProtocol());

		return RequestConfig.copy(requestConfig)
			.setProxy(proxy)
			.build();
	}

	public void configureBuilder(HttpClientBuilder httpClientBuilder) {
		if(this.proxyUser == null || this.proxyPassword == null) return;
		var credentialsProvider = new BasicCredentialsProvider();

		credentialsProvider.setCredentials(
			new AuthScope(this.getProxyHost(), this.getProxyPort()),
			new UsernamePasswordCredentials(this.getProxyUser(), this.getProxyPassword())
		);


		if (this.getProxyUser() != null && !this.getProxyUser().isBlank()) {
			httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
		}
	}

	public ProxyConfig() {
	}

	public ProxyConfig(String proxyProtocol, String proxyHost, int proxyPort, String proxyUser, String proxyPassword) {
		this.proxyProtocol = proxyProtocol;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.proxyUser = proxyUser;
		this.proxyPassword = proxyPassword;
	}

	public ProxyConfig(String proxyHost, int proxyPort) {
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
	}
}
