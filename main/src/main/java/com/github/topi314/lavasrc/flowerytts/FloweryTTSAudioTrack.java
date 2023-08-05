package com.github.topi314.lavasrc.flowerytts;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.flac.FlacAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.ogg.OggAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.wav.WavAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.BaseAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloweryTTSAudioTrack extends DelegatedAudioTrack {
    private final FloweryTTSSourceManager sourceManager;
    private final Map<String, Class<? extends BaseAudioTrack>> audioFormatMap;
    public static final String API_BASE = "https://api.flowery.pw/v1/tts";


    public FloweryTTSAudioTrack(AudioTrackInfo trackInfo, FloweryTTSSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;

        this.audioFormatMap = new HashMap<>();
        this.audioFormatMap.put("mp3", Mp3AudioTrack.class);
        this.audioFormatMap.put("ogg_opus", OggAudioTrack.class);
        this.audioFormatMap.put("ogg_vorbis", OggAudioTrack.class);
        this.audioFormatMap.put("wav", WavAudioTrack.class);
        this.audioFormatMap.put("flac", FlacAudioTrack.class);
        this.audioFormatMap.put("aac", AdtsAudioTrack.class);
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (var httpInterface = this.sourceManager.getHttpInterface()) {

            URIBuilder parsed = new URIBuilder(this.trackInfo.identifier);
            List<NameValuePair> queryParams = parsed.getQueryParams();
            URIBuilder apiUri = new URIBuilder(API_BASE);
            String format = null;

            apiUri.addParameter("text", this.trackInfo.title);
            for (NameValuePair pair : this.sourceManager.getDefaultConfig()){
                var value = queryParams.stream()
                    .filter((p) -> pair.getName().equals(p.getName()) && !"voice".equals(p.getName()))
                    .map(NameValuePair::getValue)
                    .findFirst()
                    .orElse(pair.getValue());
                apiUri.addParameter(pair.getName(), value);
                format = ("audio_format".equals(pair.getName()))? value : format;
            }
            try (var stream = new PersistentHttpStream(httpInterface, apiUri.build(), null)) {
                var audioTrackClass = this.audioFormatMap.get(format);
                if (audioTrackClass == null){
                    throw new IllegalArgumentException("Invalid audio format");
                }
                var streamClass = ("aac".equals(format)) ? InputStream.class : SeekableInputStream.class;
                processDelegate(audioTrackClass.getConstructor(AudioTrackInfo.class, streamClass).newInstance(this.trackInfo, stream), executor);
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