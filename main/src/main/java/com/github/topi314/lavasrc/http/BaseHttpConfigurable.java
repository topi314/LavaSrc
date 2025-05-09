package com.github.topi314.lavasrc.http;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

public interface BaseHttpConfigurable extends HttpConfigurable, AudioSourceManager {

	Logger log = LoggerFactory.getLogger(BaseHttpConfigurable.class);

	@NotNull
	HttpInterfaceManager getHttpInterfaceManager();

	@NotNull
	default HttpInterface getHttpInterface() {
		return this.getHttpInterfaceManager().getInterface();
	}

	@Override
	default void shutdown() {
		try {
			this.getHttpInterfaceManager().close();
		} catch (IOException e) {
			log.error("Failed to close HTTP interface manager", e);
		}
	}

	@Override
	default void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
		this.getHttpInterfaceManager().configureRequests(configurator);
	}

	@Override
	default void configureBuilder(Consumer<HttpClientBuilder> configurator) {
		this.getHttpInterfaceManager().configureBuilder(configurator);
	}
}
