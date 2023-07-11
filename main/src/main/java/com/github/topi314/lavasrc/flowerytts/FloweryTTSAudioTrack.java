package com.github.topi314.lavasrc.flowerytts;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.net.URI;
import java.util.List;

public class FloweryTTSAudioTrack extends DelegatedAudioTrack {
    private final FloweryTTSSourceManager sourceManager;
    public static final String API_BASE = "https://api.flowery.pw/v1/tts";

    public FloweryTTSAudioTrack(AudioTrackInfo trackInfo, FloweryTTSSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (var httpInterface = this.sourceManager.getHttpInterface()) {

            final URIBuilder parsed = new URIBuilder(this.trackInfo.identifier);
            final List<NameValuePair> queryParams = parsed.getQueryParams();
            final URIBuilder apiUri = new URIBuilder(API_BASE);

            apiUri.addParameter("text", this.trackInfo.title);
            apiUri.addParameter("voice", this.sourceManager.getVoice());
            for (NameValuePair pair : this.sourceManager.getDefaultConfig()){
                apiUri.addParameter(pair.getName(), queryParams.stream()
                        .filter((p) -> pair.getName().equals(p.getName()))
                        .map(NameValuePair::getValue)
                        .findFirst()
                        .orElse(pair.getValue())
                );
            }
            try (var stream = new PersistentHttpStream(httpInterface, apiUri.build(), null)) {
                processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new FloweryTTSAudioTrack(this.trackInfo, this.sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return this.sourceManager;
    }

}