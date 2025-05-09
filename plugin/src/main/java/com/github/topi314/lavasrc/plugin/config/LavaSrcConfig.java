package com.github.topi314.lavasrc.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static com.github.topi314.lavasrc.mirror.MirrorResolver.ISRC_PATTERN;
import static com.github.topi314.lavasrc.mirror.MirrorResolver.QUERY_PATTERN;

@ConfigurationProperties(prefix = "plugins.lavasrc")
@Component
public class LavaSrcConfig {

	private String[] resolvers = {
		"ytsearch:\"" + ISRC_PATTERN + "\"",
		"ytsearch:" + QUERY_PATTERN
	};

	public String[] getResolvers() {
		return this.resolvers;
	}

	public void setResolvers(String[] resolvers) {
		this.resolvers = resolvers;
	}

}
