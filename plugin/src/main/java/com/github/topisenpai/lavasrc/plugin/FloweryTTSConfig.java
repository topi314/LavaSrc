package com.github.topisenpai.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.flowerytts")
@Component
public class FloweryTTSConfig {

    private final int SILENCE_MIN = 0;
    private final int SILENCE_MAX = 10000;
    private final float SPEED_MIN = 0.5f;
    private final float SPEED_MAX = 10;

    private String voice;
    private boolean translate;
    private int silence;
    private float speed;

    public String getVoice() {
        return this.voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public boolean getTranslate() {
        return this.translate;
    }

    public void setTranslate(boolean translate) {
        this.translate = translate;
    }

    public int getSilence(){
        return this.silence;
    }

    public void setSilence(int silence){
        this.silence = silence > SILENCE_MAX ? SILENCE_MAX : silence < SILENCE_MIN ? SILENCE_MIN : silence;
    }

    public float getSpeed(){
        return this.speed;
    }

    public void setSpeed(float speed){
        this.speed = speed > SPEED_MAX ? SPEED_MAX : speed < SPEED_MIN ? SPEED_MIN : speed;
    }
}