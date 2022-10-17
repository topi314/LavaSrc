package com.github.topisenpai.lavasrc.yandexmusic;

import com.github.topisenpai.lavasrc.mirror.TrackNotFoundException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;

public class YandexMusicAudioTrack extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(YandexMusicAudioTrack.class);

    private final String songId;
    private final String artworkURL;
    private final YandexMusicSourceManager sourceManager;

    public YandexMusicAudioTrack(AudioTrackInfo trackInfo, String songId, String artworkURL, YandexMusicSourceManager sourceManager) {
        super(trackInfo);
        this.songId = songId;
        this.artworkURL = artworkURL;
        this.sourceManager = sourceManager;
    }

    public String getSongId() {
        return this.songId;
    }

    public String getArtworkURL() {
        return this.artworkURL;
    }

    private String getTrackTitle() {
        var query = this.trackInfo.title;
        if (!this.trackInfo.author.isEmpty()) {
            query += " " + this.trackInfo.author;
        }
        return query;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        AudioItem track = null;

        var downloadLink = this.getDownloadURL(this.songId);
        track = loadItem(downloadLink);

        if (track != AudioReference.NO_TRACK) {
            if (track instanceof AudioPlaylist) {
                track = ((AudioPlaylist) track).getTracks().get(0);
            }
            if (track instanceof InternalAudioTrack) {
                processDelegate((InternalAudioTrack) track, executor);
                return;
            }
        }
        throw new FriendlyException("No matching track found", FriendlyException.Severity.COMMON, new TrackNotFoundException());
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new YandexMusicAudioTrack(this.trackInfo, this.songId, this.artworkURL, this.sourceManager);
    }

    private AudioItem loadItem(String query) {
        var cf = new CompletableFuture<AudioItem>();
        this.sourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(AudioTrack track) {
                cf.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                cf.complete(playlist);
            }

            @Override
            public void noMatches() {
                cf.complete(AudioReference.NO_TRACK);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                cf.completeExceptionally(exception);
            }
        });
        return cf.join();
    }

    private String getDownloadURL(String id) throws IOException, NoSuchAlgorithmException {
        var json = this.sourceManager.getJson(YandexMusicSourceManager.PUBLIC_API_BASE + "/tracks/" + id + "/download-info");
        if (json == null || json.get("result").values().isEmpty())
            throw new FriendlyException("No download URL found for track " + id, FriendlyException.Severity.COMMON, new YandexMusicTrackLoadingException());

        var downloadInfoLink = json.get("result").values().get(0).get("downloadInfoUrl").text();
        String downloadInfo = this.sourceManager.getDownloadStrings(downloadInfoLink);
        if (downloadInfo == null)
            throw new FriendlyException("No download URL found for track " + id, FriendlyException.Severity.COMMON, new YandexMusicTrackLoadingException());

        var host = downloadInfo.substring(downloadInfo.indexOf("<host>") + 6, downloadInfo.indexOf("</host>"));
        var path = downloadInfo.substring(downloadInfo.indexOf("<path>") + 6, downloadInfo.indexOf("</path>"));
        var ts = downloadInfo.substring(downloadInfo.indexOf("<ts>") + 4, downloadInfo.indexOf("</ts>"));
        var s = downloadInfo.substring(downloadInfo.indexOf("<s>") + 3, downloadInfo.indexOf("</s>"));

        var sign = "XGRlBW9FXlekgbPrRHuSiA" + path + s;
        var md = MessageDigest.getInstance("MD5");
        var digest = md.digest(sign.getBytes(StandardCharsets.UTF_8));
        var sb = new StringBuilder();
        for (byte b : digest)
            sb.append(String.format("%02x", b));
        var md5 = sb.toString();

        return "https://" + host + "/get-mp3/" + md5 + "/" + ts + path;
    }
}