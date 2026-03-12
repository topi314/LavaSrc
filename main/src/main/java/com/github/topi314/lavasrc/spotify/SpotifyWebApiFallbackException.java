package com.github.topi314.lavasrc.spotify;

import org.apache.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Indicates that the Spotify Web API returned a response that should fall back to the Partner API.
 */
public class SpotifyWebApiFallbackException extends RuntimeException {

    private static final String PREMIUM_REQUIRED_MESSAGE = "active premium subscription required for the owner of the app";

    private final int statusCode;
    @Nullable
    private final String responseBody;

    public SpotifyWebApiFallbackException(int statusCode, @Nullable String responseBody) {
        super("Spotify Web API response requires Partner API fallback.");
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    @Nullable
    public String getResponseBody() {
        return this.responseBody;
    }

    public String getReason() {
        if (this.responseBody == null || this.responseBody.isBlank()) {
            return "empty response body";
        }

        String normalized = this.responseBody.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    public static boolean shouldFallbackToPartnerApi(int statusCode, @Nullable String responseBody) {
        if (statusCode == HttpStatus.SC_TOO_MANY_REQUESTS) {
            return true;
        }

        if (statusCode != HttpStatus.SC_FORBIDDEN || responseBody == null) {
            return false;
        }

        return responseBody.toLowerCase(Locale.ROOT).contains(PREMIUM_REQUIRED_MESSAGE);
    }
}
