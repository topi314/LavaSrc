package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.deezer")
@Component
public class DeezerConfig {

    private String masterDecryptionKey;
    private String arl;

    public String getMasterDecryptionKey() {
        return this.masterDecryptionKey;
    }

    public String getArl() {
        return this.arl;
    }

    public void setMasterDecryptionKey(String masterDecryptionKey) {
        this.masterDecryptionKey = masterDecryptionKey;
    }

    public void setArl(String arl) {
        this.arl = arl;
    }
}