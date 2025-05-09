package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.source.deezer.DeezerAudioTrack;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.deezer")
@Component
public class DeezerConfig {

	private boolean enabled = false;
	private boolean lavaLyricsEnabled = false;
	private boolean lavaSearchEnabled = false;
	private String masterDecryptionKey;
	private String arl;
	private DeezerAudioTrack.TrackFormat[] formats;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isLavaLyricsEnabled() {
		return this.lavaLyricsEnabled;
	}

	public void setLavaLyricsEnabled(boolean lavaLyricsEnabled) {
		this.lavaLyricsEnabled = lavaLyricsEnabled;
	}

	public boolean isLavaSearchEnabled() {
		return this.lavaSearchEnabled;
	}

	public void setLavaSearchEnabled(boolean lavaSearchEnabled) {
		this.lavaSearchEnabled = lavaSearchEnabled;
	}

	public String getMasterDecryptionKey() {
		return this.masterDecryptionKey;
	}

	public void setMasterDecryptionKey(String masterDecryptionKey) {
		this.masterDecryptionKey = masterDecryptionKey;
	}

	public String getArl() {
		return this.arl;
	}

	public void setArl(String arl) {
		this.arl = arl;
	}

	public DeezerAudioTrack.TrackFormat[] getFormats() {
		return this.formats;
	}

	public void setFormats(DeezerAudioTrack.TrackFormat[] formats) {
		this.formats = formats;
	}

}
