package com.github.topisenpai.lavasrc.deezer;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DeezerAudioTrack extends DelegatedAudioTrack {

    private final String isrc;
    private final String artworkURL;
    private final DeezerAudioSourceManager sourceManager;

    public DeezerAudioTrack(AudioTrackInfo trackInfo, String isrc, String artworkURL, DeezerAudioSourceManager sourceManager) {
        super(trackInfo);
        this.isrc = isrc;
        this.artworkURL = artworkURL;
        this.sourceManager = sourceManager;
    }

    public String getISRC() {
        return isrc;
    }

    public String getArtworkURL() {
        return artworkURL;
    }

    private URI getTrackMediaURI() throws IOException, URISyntaxException {
        HttpPost getSessionID = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.ping&input=3&api_version=1.0&api_token=");
        JsonBrowser json = HttpClientTools.fetchResponseAsJson(sourceManager.getHttpInterface(), getSessionID);
        if (json.get("data").index(0).get("errors").index(0).get("code").asLong(0) != 0) throw new IllegalStateException("Failed to get session ID");
        String sessionID = json.get("results").get("SESSION").text();

        HttpPost getUserToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=");
        getUserToken.setHeader("Cookie", "sid=" + sessionID);
        json = HttpClientTools.fetchResponseAsJson(sourceManager.getHttpInterface(), getUserToken);
        if (json.get("data").index(0).get("errors").index(0).get("code").asLong(0) != 0) throw new IllegalStateException("Failed to get user token");
        String userLicenseToken = json.get("results").get("USER").get("OPTIONS").get("license_token").text();
        String apiToken = json.get("results").get("checkForm").text();

        HttpPost getTrackToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=song.getData&input=3&api_version=1.0&api_token=" + apiToken);
        getTrackToken.setEntity(new StringEntity("{\"sng_id\":\"" + trackInfo.identifier + "\"}", ContentType.APPLICATION_JSON));
        json = HttpClientTools.fetchResponseAsJson(sourceManager.getHttpInterface(), getTrackToken);
        if (json.get("data").index(0).get("errors").index(0).get("code").asLong(0) != 0) throw new IllegalStateException("Failed to get track token");
        String trackToken = json.get("results").get("TRACK_TOKEN").text();

        HttpPost getMediaURL = new HttpPost(DeezerAudioSourceManager.MEDIA_BASE + "/get_url");
        getMediaURL.setEntity(new StringEntity("{\"license_token\":\"" + userLicenseToken + "\",\"media\": [{\"type\": \"FULL\",\"formats\": [{\"cipher\": \"BF_CBC_STRIPE\", \"format\": \"MP3_128\"}]}],\"track_tokens\": [\"" + trackToken + "\"]}", ContentType.APPLICATION_JSON));
        json = HttpClientTools.fetchResponseAsJson(sourceManager.getHttpInterface(), getMediaURL);
        if (json.get("data").index(0).get("errors").index(0).get("code").asLong(0) != 0) throw new IllegalStateException("Failed to get media URL");
        return new URI(json.get("data").index(0).get("media").index(0).get("sources").index(0).get("url").text());
    }

    private byte[] getTrackDecryptionKey() throws NoSuchAlgorithmException {
        char[] md5 = Hex.encodeHex(MessageDigest.getInstance("MD5").digest(this.trackInfo.identifier.getBytes()), true);
        byte[] master_key = this.sourceManager.getMasterDecryptionKey().getBytes();

        byte[] key = new byte[16];
        for (int i = 0; i < 16; i++) {
            key[i] = (byte) (md5[i] ^ md5[i + 16] ^ master_key[i]);
        }
        return key;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            try (DeezerPersistentHttpStream stream = new DeezerPersistentHttpStream(httpInterface, getTrackMediaURI(), trackInfo.length, getTrackDecryptionKey())) {
                processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new DeezerAudioTrack(trackInfo, isrc, artworkURL, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

}
