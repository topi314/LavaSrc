package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.jiosaavn.JioSaavnDecryptionConfig;
import com.github.topi314.lavasrc.proxy.ProxyConfig;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.jiosaavn")
@Component
public class JioSaavnConfig {
	@Nullable
	private ProxyConfig proxy;
	private JioSaavnDecryptionConfig decryption;

	@Nullable
	public ProxyConfig getProxy() {
		return this.proxy;
	}

	public void setProxy(@Nullable ProxyConfig proxy) {
		this.proxy = proxy;
	}

	public JioSaavnDecryptionConfig getDecryption() {
		return decryption;
	}

	public void setDecryption(JioSaavnDecryptionConfig decryption) {
		this.decryption = decryption;
	}
}
