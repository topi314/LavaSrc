package com.github.topi314.lavasrc.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.flowery.tts")
@Component
public class FloweryTTSConfig {

    private static final int SILENCE_MIN = 0;
    private static final int SILENCE_MAX = 10000;
    private static final float SPEED_MIN = 0.5f;
    private static final float SPEED_MAX = 10;

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
        this.silence = Math.max(SILENCE_MIN, Math.min(SILENCE_MAX, silence));
    }

    public float getSpeed(){
        return this.speed;
    }

    public void setSpeed(float speed){
        this.speed = Math.max(SPEED_MIN, Math.min(SPEED_MAX, speed));
    }
}