package com.github.topi314.lavasrc;

import org.jetbrains.annotations.Nullable;

/**
 * Indicates that the upstream API responded with a rate limit (HTTP 429).
 */
public class RateLimitException extends RuntimeException {

	private final int statusCode;
	@Nullable
	private final Long retryAfterSeconds;
	@Nullable
	private final String responseBody;

	public RateLimitException(String message, int statusCode, @Nullable Long retryAfterSeconds,
			@Nullable String responseBody, @Nullable Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
		this.retryAfterSeconds = retryAfterSeconds;
		this.responseBody = responseBody;
	}

	public int getStatusCode() {
		return statusCode;
	}

	@Nullable
	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

	@Nullable
	public String getResponseBody() {
		return responseBody;
	}
}

