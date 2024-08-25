package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.deezer.DeezerAudioTrack;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.deezer")
@Component
public class DeezerConfig {

	private String masterDecryptionKey;
	private String arl;
	private DeezerAudioTrack.TrackFormat[] formats;

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
