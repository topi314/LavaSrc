package com.github.topisenpai.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.applemusic")
@Component
public class AppleMusicConfig {

    private String countryCode = "us";
    private String mediaAPIToken;

    public String getCountryCode() {
        return this.countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getMediaAPIToken() {
        return this.mediaAPIToken;
    }

    public void setMediaAPIToken(String mediaAPIToken) {
        this.mediaAPIToken = mediaAPIToken;
    }

}
