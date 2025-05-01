package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.jiosaavn.JioSaavnDecryptionConfig;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.jiosaavn")
@Component
public class JioSaavnConfig {
	@Nullable
	private HttpProxyConfig proxy;
	private JioSaavnDecryptionConfig decryption;

	@Nullable
	public HttpProxyConfig getProxy() {
		return this.proxy;
	}

	@SuppressWarnings("unused")
	public void setProxy(@Nullable HttpProxyConfig proxy) {
		this.proxy = proxy;
	}

	public JioSaavnDecryptionConfig getDecryption() {
		return decryption;
	}

	@SuppressWarnings("unused")
	public void setDecryption(JioSaavnDecryptionConfig decryption) {
		this.decryption = decryption;
	}
}
