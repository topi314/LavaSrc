package com.github.topislavalinkplugins.spotify;

import com.neovisionaries.i18n.CountryCode;

import static com.github.topislavalinkplugins.spotify.SpotifySourceManager.ISRC_PATTERN;
import static com.github.topislavalinkplugins.spotify.SpotifySourceManager.QUERY_PATTERN;

public class SpotifyConfig{

	private String clientId;
	private String clientSecret;
	private CountryCode countryCode = CountryCode.US;
	private String[] providers = {
		"ytsearch:\"" + ISRC_PATTERN + "\"",
		"ytsearch:" + QUERY_PATTERN
	};

	public SpotifyConfig(){}

	public SpotifyConfig(String clientId, String clientSecret, CountryCode countryCode, String[] providers){
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.countryCode = countryCode;
		this.providers = providers;
	}

	public String getClientId(){
		return this.clientId;
	}

	public void setClientId(String clientId){
		this.clientId = clientId;
	}

	public String getClientSecret(){
		return this.clientSecret;
	}

	public void setClientSecret(String clientSecret){
		this.clientSecret = clientSecret;
	}

	public CountryCode getCountryCode(){
		return this.countryCode;
	}

	public void setCountryCode(String countryCode){
		this.countryCode = CountryCode.getByCode(countryCode);
	}

	public String[] getProviders(){
		return providers;
	}

	public void setProviders(String[] providers){
		this.providers = providers;
	}

}
