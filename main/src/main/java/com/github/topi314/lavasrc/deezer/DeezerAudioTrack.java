package com.github.topi314.lavasrc.deezer;

import com.github.topi314.lavasrc.ExtendedAudioTrack;
import com.github.topi314.lavasrc.LavaSrcTools;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;


public class DeezerAudioTrack extends ExtendedAudioTrack {

    private final DeezerAudioSourceManager sourceManager;
		private static final Logger log = LoggerFactory.getLogger(DeezerAudioTrack.class);
	private JsonBrowser json;

    public DeezerAudioTrack(AudioTrackInfo trackInfo, DeezerAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, null, false, sourceManager);
    }

    public DeezerAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl, String artistArtworkUrl, String previewUrl, boolean isPreview, DeezerAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
        this.sourceManager = sourceManager;
    }

 private URI getTrackMediaURI() throws IOException, URISyntaxException {
        var getSessionID = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.ping&input=3&api_version=1.0&api_token=");
        var json = LavaSrcTools.fetchResponseAsJson(this.sourceManager.getHttpInterface(), getSessionID);

        this.checkResponse(json, "Failed to get session ID: ");
        var sessionID = json.get("results").get("SESSION").text();

var getUserToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=");
getUserToken.setHeader("Cookie", "arl=" + "354d8172ee449927de992e3c905e2598378f8a05c00e6b052e26b98fcab61326e8861137e221303286f9293df1d5be1dbc335619d8e9b9878c715923f4539fc35b18eb2070ae6502415dee70bcfc4d8f91c1686477a290a49366ef311a39d927"); // Replace with your actual access token
json = LavaSrcTools.fetchResponseAsJson(this.sourceManager.getHttpInterface(), getUserToken);

// Log the entire formatted JSON response for getUserToken
log.info("getUserToken API Response: {}", json.format());

this.checkResponse(json, "Failed to get user token: ");
var userLicenseToken = json.get("results").get("USER").get("OPTIONS").get("license_token").text();
var apiToken = json.get("results").get("checkForm").text();


        var getTrackToken = new HttpPost(DeezerAudioSourceManager.PRIVATE_API_BASE + "?method=song.getData&input=3&api_version=1.0&api_token=" + apiToken);
getTrackToken.setEntity(new StringEntity("{\"sng_id\":\"" + this.trackInfo.identifier + "\"}", ContentType.APPLICATION_JSON));
		getTrackToken.setHeader("Cookie", "arl=" + "354d8172ee449927de992e3c905e2598378f8a05c00e6b052e26b98fcab61326e8861137e221303286f9293df1d5be1dbc335619d8e9b9878c715923f4539fc35b18eb2070ae6502415dee70bcfc4d8f91c1686477a290a49366ef311a39d927");

json = LavaSrcTools.fetchResponseAsJson(this.sourceManager.getHttpInterface(), getTrackToken);

log.info("Track Token API Response: {}", json.format());

this.checkResponse(json, "Failed to get track token: ");
var trackToken = json.get("results").get("TRACK_TOKEN").text();
var SNG_ID = json.get("results").get("SNG_ID").text();
var MD5_ORIGIN = json.get("FALLBACK").get("MD5_ORIGIN").text();
var MEDIA_VERSION = json.get("results").get("MEDIA_VERSION").text();

// Log information
log.info("Track Token: {}", trackToken);
log.info("SNG_ID: {}", SNG_ID);
log.info("MD5_ORIGIN: {}", MD5_ORIGIN);
log.info("MEDIA_VERSION: {}", MEDIA_VERSION);

// URL Generation
String cdn = String.valueOf(MD5_ORIGIN.charAt(0));
String filename;
try {
    filename = getSongFileName(MD5_ORIGIN, SNG_ID, MEDIA_VERSION);
    log.info("Generated Filename: {}", filename);
} catch (Exception e) {
    // Handle the exception appropriately, for example, logging or rethrowing
    log.error("Failed to generate filename", e);
    throw new IOException("Failed to generate filename", e);
}

var mediaURL = "http://e-cdn-proxy-" + cdn + ".deezer.com/mobile/1/" + filename;
log.info("Generated Media URL: {}", mediaURL);

return new URI(mediaURL);
}

    private void checkResponse(JsonBrowser json, String message) throws IllegalStateException {
        if (json == null) {
            throw new IllegalStateException(message + "No response");
        }
        var errors = json.get("data").index(0).get("errors").values();
        if (!errors.isEmpty()) {
            var errorsStr = errors.stream().map(error -> error.get("code").text() + ": " + error.get("message").text()).collect(Collectors.joining(", "));
            throw new IllegalStateException(message + errorsStr);
        }
    }

    private byte[] getTrackDecryptionKey() throws NoSuchAlgorithmException {
        var md5 = Hex.encodeHex(MessageDigest.getInstance("MD5").digest(this.trackInfo.identifier.getBytes()), true);
        var master_key = this.sourceManager.getMasterDecryptionKey().getBytes();

        var key = new byte[16];
        for (int i = 0; i < 16; i++) {
            key[i] = (byte) (md5[i] ^ md5[i + 16] ^ master_key[i]);
        }
        return key;
    }

    private String getSongFileName(String MD5_ORIGIN, String SNG_ID, String MEDIA_VERSION) throws Exception {
        String step1 = String.join("¤", MD5_ORIGIN, "1", SNG_ID, MEDIA_VERSION);

        String step2 = Hex.encodeHexString(MessageDigest.getInstance("MD5").digest(step1.getBytes())) + "¤" + step1 + "¤";
        while (step2.length() % 16 > 0) {
            step2 += " ";
        }

        SecretKeySpec keySpec = new SecretKeySpec("jo6aey6haid2Teih".getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);

        byte[] encryptedBytes = cipher.doFinal(step2.getBytes("ASCII"));
        return Hex.encodeHexString(encryptedBytes);
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        try (var httpInterface = this.sourceManager.getHttpInterface()) {
            if (this.isPreview) {
                if (this.previewUrl == null) {
                    throw new FriendlyException("No preview url found", FriendlyException.Severity.COMMON, new IllegalArgumentException());
                }
                try (var stream = new PersistentHttpStream(httpInterface, new URI(this.previewUrl), this.trackInfo.length)) {
                    processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
                }
            } else {
                var mediaURI = getTrackMediaURI();
                try (var stream = new PersistentHttpStream(httpInterface, mediaURI, this.trackInfo.length)) {
                    processDelegate(new Mp3AudioTrack(this.trackInfo, stream), executor);
                }
            }
        }
    }
    @Override
    protected AudioTrack makeShallowClone() {
        return new DeezerAudioTrack(this.trackInfo, this.albumName, this.albumUrl, this.artistUrl, this.artistArtworkUrl, this.previewUrl, this.isPreview, this.sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return this.sourceManager;
    }

}
