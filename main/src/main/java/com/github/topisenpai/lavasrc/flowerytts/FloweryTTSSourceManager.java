package com.github.topisenpai.lavasrc.flowerytts;

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
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
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
import java.util.function.Consumer;
import java.util.function.Function;

public class FloweryTTSSourceManager implements AudioSourceManager, HttpConfigurable {

    public static final String TTS_PREFIX = "tts://";
    public static final String API_BASE = "https://api.flowery.pw/v1/tts";
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

    @Override
    public String getSourceName(){
        return "flowerytts";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference){

        if (reference.identifier.startsWith(TTS_PREFIX)) {
            final String query = reference.identifier.substring(TTS_PREFIX.length());
            final URI uri = this.buildURI(query);

            return new FloweryTTSAudioTrack(
                    new AudioTrackInfo(
                            "text-to-speech",
                            "FloweryTTS",
                            Units.CONTENT_LENGTH_UNKNOWN,
                            "no-id",
                            false,
                            uri.toString()),
                    this);
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

    private URI buildURI(String query){
        try {
            // tts://text%20to%20speech%20lol?hello=world&hello2=world

            final URIBuilder parsed = new URIBuilder(query);
            final List<NameValuePair> queryParams = parsed.getQueryParams();

            var text = (queryParams.isEmpty()) ?  query : query.substring(0, query.indexOf('?'));
            final URIBuilder finalUri = new URIBuilder(API_BASE + "?text=" + text);

            finalUri.addParameter("voice", queryParams.stream()
                    .filter((p) -> "voice".equals(p.getName()))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(this.voice)
            );
            finalUri.addParameter("translate", queryParams.stream()
                    .filter((p) -> "translate".equals(p.getName()))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(Boolean.toString(this.translate))
            );
            finalUri.addParameter("silence", queryParams.stream()
                    .filter((p) -> "silence".equals(p.getName()))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(Integer.toString(this.silence))
            );
            finalUri.addParameter("speed", queryParams.stream()
                    .filter((p) -> "speed".equals(p.getName()))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(Float.toString(this.speed))
            );
            return finalUri.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }
}