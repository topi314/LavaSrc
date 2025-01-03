package com.github.topi314.lavasrc.proxy;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;


public class ProxyManager {
	private static final Logger log = LoggerFactory.getLogger(ProxyManager.class);

	private final List<HttpInterfaceManager> httpInterfaceManagers = new CopyOnWriteArrayList<>();
	private int nextManagerIndex = 0;
	private HttpInterfaceManager localManager;


	public ProxyManager(ProxyConfig[] configs, boolean useLocalnetwork) {
		for (var config : configs) {
			var manager = HttpClientTools.createCookielessThreadLocalManager();
			manager.configureRequests(config::configureRequest);
			manager.configureBuilder(config::configureBuilder);
			this.httpInterfaceManagers.add(manager);
		}

		this.localManager = HttpClientTools.createCookielessThreadLocalManager();
		if(useLocalnetwork) {
			this.httpInterfaceManagers.add(localManager);
			log.debug("Created local proxy manager");
		}

		log.debug("Created {} proxy managers", httpInterfaceManagers);
	}

	public synchronized HttpInterfaceManager getHttpInterfaceManager() {
		var manager = httpInterfaceManagers.get(nextManagerIndex);
		log.debug("Using proxied interface manager number {}", nextManagerIndex);
		nextManagerIndex = (nextManagerIndex + 1) % httpInterfaceManagers.size();
		return manager;
	}

	public HttpInterfaceManager getLocalManager() {
		log.debug("Using local proxy manager");
		return localManager;
	}

	public void close() throws IOException {
		for(var manager : httpInterfaceManagers) {
			try {
				manager.close();
			} catch (IOException e) {
				log.error("Failed to close HTTP interface manager", e);
			}
		}
	}

	public void configureAllRequests(Function<RequestConfig, RequestConfig> configurator) {
		httpInterfaceManagers.forEach(manager -> manager.configureRequests(configurator));
	}

	public void configureAllBuilder(Consumer<HttpClientBuilder> configurator) {
		httpInterfaceManagers.forEach(manager -> manager.configureBuilder(configurator));
	}
}