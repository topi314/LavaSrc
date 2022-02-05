package com.github.topislavalinkplugins.sourcemanagers.applemusic;

import org.apache.hc.core5.http.HttpException;

public class AppleMusicWebAPIException extends HttpException{

	public AppleMusicWebAPIException(String message){
		super(message);
	}

}
