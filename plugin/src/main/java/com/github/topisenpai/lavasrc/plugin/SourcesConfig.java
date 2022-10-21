package com.github.topisenpai.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@ConfigurationProperties(prefix = "plugins.lavasrc.sources")
@Component
public class SourcesConfig {

    private boolean spotify = true;
    private boolean appleMusic = true;
    private boolean yandexMusic = true;
    private boolean deezer = true;

    public boolean isSpotify() {
        return this.spotify;
    }

    public void setSpotify(boolean spotify) {
        this.spotify = spotify;
    }

    public boolean isAppleMusic() {
        return this.appleMusic;
    }

    public void setAppleMusic(boolean appleMusic) {
        this.appleMusic = appleMusic;
    }

    public boolean isDeezer() {
        return this.deezer;
    }

    public void setDeezer(boolean deezer) {
        this.deezer = deezer;
    }

    public boolean isYandexMusic() {
        return this.yandexMusic;
    }

    public void setYandexMusic(boolean yandexMusic) {
        this.yandexMusic = yandexMusic;
    }
}
