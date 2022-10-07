package com.github.topisenpai.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.deezer")
@Component
public class DeezerConfig {

    private String masterDecryptionKey;

    public String getMasterDecryptionKey() {
        return this.masterDecryptionKey;
    }

    public void setMasterDecryptionKey(String masterDecryptionKey) {
        this.masterDecryptionKey = masterDecryptionKey;
    }

}
