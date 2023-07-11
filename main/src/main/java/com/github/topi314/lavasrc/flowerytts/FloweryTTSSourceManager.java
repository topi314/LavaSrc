package com.github.topi314.lavasrc.flowerytts;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Boolean;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

public class FloweryTTSSourceManager implements AudioSourceManager, HttpConfigurable {

    public static final String TTS_PREFIX = "tts://";
    private static final Logger log = LoggerFactory.getLogger(FloweryTTSSourceManager.class);

    private final String voice;
    private boolean translate = false;
    private int silence = 0;
    private float speed = 1;

    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

    public FloweryTTSSourceManager(String voice) {
        if (voice == null || voice.isEmpty()) {
            throw new IllegalArgumentException("Default voice must be set");
        }
        this.voice = voice;
    }

    public void setTranslate(boolean translate) {
        this.translate = translate;
    }

    public void setSilence(int silence) {
        this.silence = silence;
    }

    public void setSpeed (float speed) {
        this.speed = speed;
    }

    public List<NameValuePair> getDefaultConfig() {
        final List<NameValuePair> config = new ArrayList<NameValuePair>(3);
        config.add(new BasicNameValuePair("translate", Boolean.toString(this.translate)));
        config.add(new BasicNameValuePair("silence", Integer.toString(this.silence)));
        config.add(new BasicNameValuePair("speed", Float.toString(this.speed)));
        return config;
    }

    public String getVoice() {
        return voice;
    }

    @Override
    public String getSourceName(){
        return "flowerytts";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference){

        if (reference.identifier.startsWith(TTS_PREFIX)) {
            try {
                URI queryUri = new URI(reference.identifier);

                if (queryUri.getAuthority() == null)
                    return null;

                return new FloweryTTSAudioTrack(
                        new AudioTrackInfo(
                                queryUri.getAuthority(),
                                "FloweryTTS",
                                Units.CONTENT_LENGTH_UNKNOWN,
                                queryUri.toString(),
                                false,
                                null),
                        this);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track){
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output){
        // nothing to encode
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input){
        return new FloweryTTSAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        try {
            this.httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close HTTP interface manager", e);
        }
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        this.httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        this.httpInterfaceManager.configureBuilder(configurator);
    }

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }

}