package com.github.topi314.lavasrc.yandexmusic.radio;

import com.github.topi314.lavasrc.yandexmusic.YandexMusicSourceManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

public class YandexMusicAudioRadio implements AudioItem, AudioTrack {

    private final String name;
    private final String station;
    private final String batchId;
    private List<AudioTrack> stationTracks;
    private final YandexMusicSourceManager sourceManager;
    private AudioTrack currentTrack;
    private int index = 0;
    private static final String HREF = "https://music.yandex.ru/";

    public YandexMusicAudioRadio(String name, String station, String batchId, List<AudioTrack> stationTracks, YandexMusicSourceManager sourceManager) {
        this.name = name;
        this.station = station;
        this.batchId = batchId;
        this.stationTracks = stationTracks;
        this.sourceManager = sourceManager;
    }

    public String getName() {
        return this.name;
    }

    public String getUrl() {
        var type = station.split(":")[0];
        var tag = station.split(":")[1].split("_");
        switch (type) {
            case "playlist":
                return HREF + "users/" + tag[0] + "/playlists/" + tag[1];
            case "album":
                return HREF + "album/" + tag[0];
            case "track":
                return HREF + "track/" + tag[0];
            case "artist":
                return HREF + "artist/" + tag[0];
            default:
                return null;
        }
    }

    public String getStation() {
        return this.station;
    }

    public String getBatchId() {
        return this.batchId;
    }

    public AudioTrack nextTrack() throws URISyntaxException, IOException {
        if (Objects.isNull(currentTrack)) {
            this.sendStartRadio();
        }
        else {
            this.sendPlayEndRadio();
            this.index += 1;
            if (this.index >= stationTracks.size()) {
                this.updateRadioBatch();
            }
        }
        currentTrack = this.updateCurrentTrack();
        return currentTrack;
    }

    public AudioTrack skipTrack() throws URISyntaxException, IOException {
        if (Objects.isNull(currentTrack)) {
            this.sendStartRadio();
            this.updateCurrentTrack();
        } else {
            this.sendPlaySkipRadio();
            this.index += 1;
            if (this.index >= stationTracks.size()) {
                this.updateRadioBatch();
            }
            this.updateCurrentTrack();
        }
        return currentTrack;
    }

    public AudioTrack getCurrent() {
        return currentTrack;
    }

    public List<AudioTrack> getStationTracks() {
        return stationTracks;
    }

    public int getIndex() {
        return index;
    }

    private void updateRadioBatch() throws IOException, URISyntaxException {
        this.index = 0;
        AudioRadioBatch radioBatch = this.sourceManager.getRadioBatch(this.station, currentTrack != null ? currentTrack.getIdentifier() : null);
        this.stationTracks = radioBatch.getAudioTracks();
        this.sendStartRadio();
    }

    private AudioTrack updateCurrentTrack() throws URISyntaxException, IOException {
        currentTrack = stationTracks.get(index);
        this.sendPlayStartRadio();
        return currentTrack;
    }

    private void sendStartRadio() throws IOException, URISyntaxException {
        sourceManager.radioFeedBackRadioStarted(station, batchId);
    }

    private void sendPlayEndRadio() throws URISyntaxException, IOException {
        sourceManager.radioFeedBackTrackFinished(station, currentTrack, currentTrack.getPosition() / 1000, batchId);
    }

    private void sendPlayStartRadio() throws URISyntaxException, IOException {
        sourceManager.radioFeedBackTrackStarted(station, currentTrack, batchId);
    }

    private void sendPlaySkipRadio() throws URISyntaxException, IOException {
        sourceManager.radioFeedBackTrackSkipped(station, currentTrack, currentTrack.getPosition() / 1000, batchId);
    }

    public AudioTrackInfo getInfo() {
        return currentTrack.getInfo();
    }

    public String getIdentifier() {
        return currentTrack.getIdentifier();
    }

    public AudioTrackState getState() {
        return currentTrack.getState();
    }

    public void stop() {
        currentTrack.stop();
    }

    public boolean isSeekable() {
        return false;
    }

    public long getPosition() {
        return currentTrack.getPosition();
    }

    public void setPosition(long position) {
        currentTrack.setPosition(position);
    }

    public void setMarker(TrackMarker marker) {
        currentTrack.setMarker(marker);
    }

    public long getDuration() {
        return currentTrack.getDuration();
    }

    public AudioTrack makeClone() {
        return currentTrack.makeClone();
    }

    public AudioSourceManager getSourceManager() {
        return currentTrack.getSourceManager();
    }

    public void setUserData(Object userData) {
        currentTrack.setUserData(userData);
    }

    public Object getUserData() {
        return currentTrack.getUserData();
    }

    public <T> T getUserData(Class<T> klass) {
        return currentTrack.getUserData(klass);
    }
}
