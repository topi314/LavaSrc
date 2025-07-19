package com.github.topi314.lavasrc.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.amazonmusic")
@Component
public class AmazonMusicConfig {

    /**
     * The base URL of your Amazon Music API. Required.
     */
    private String apiUrl;

    /**
     * Optional. Only needed if your API requires an authentication key.
     */
    private String apiKey;

    public String getApiUrl() {
        return apiUrl;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("apiUrl")
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("apiKey")
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
