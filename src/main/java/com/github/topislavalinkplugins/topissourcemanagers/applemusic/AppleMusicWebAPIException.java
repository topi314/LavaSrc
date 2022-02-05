package com.github.topislavalinkplugins.topissourcemanagers.applemusic;

import org.apache.hc.core5.http.HttpException;

public class AppleMusicWebAPIException extends HttpException{

	public AppleMusicWebAPIException(String message){
		super(message);
	}

}
