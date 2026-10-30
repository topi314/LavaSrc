package com.github.topi314.lavasrc;

public class HttpStatusException extends IllegalStateException {

	private final int statusCode;

	public HttpStatusException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return this.statusCode;
	}
}
