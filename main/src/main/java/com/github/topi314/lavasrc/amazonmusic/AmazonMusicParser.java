package com.github.topi314.lavasrc.amazonmusic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Amazon Music metadata from a raw JSON string.
 * This version uses manual parsing without any external JSON libraries.
 */
public class AmazonMusicParser {

	// Regex patterns to extract the artist, title and audio URL
	private static final Pattern ARTIST_PATTERN = Pattern.compile("\"artist\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern AUDIO_URL_PATTERN = Pattern.compile("\"audioUrl\"\\s*:\\s*\"(.*?)\"");
	// Regex pattern to extract the artwork URL
	private static final Pattern ARTWORK_URL_PATTERN = Pattern.compile("\"image\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern ARTIST_NAME_PATTERN = Pattern.compile("\"artist\"\\s*:\\s*\\{.*?\"name\"\\s*:\\s*\"(.*?)\"");
	private static final Pattern ISRC_PATTERN = Pattern.compile("\"isrc\"\\s*:\\s*\"(.*?)\"");

	/**
	 * Parses a JSON string returned from Amazon Music and extracts the song title and artist.
	 * Expected JSON structure (simplified example):
	 * {
	 *   "catalog": {
	 *     "title": {
	 *       "name": "Song Name",
	 *       "artist": "Artist Name",
	 *       "audioUrl": "https://example.com/path/to/audio.mp3"
	 *     }
	 *   }
	 * }
	 *
	 * @param json the raw JSON response as a string
	 * @return formatted title in the form "Artist - Song Name" or "Unknown Title" if parsing fails
	 */
	public static String parseAmazonTitle(String json) {
		if (json == null || json.isEmpty()) {
			System.err.println("[AmazonMusicParser] [ERROR] JSON is null or empty.");
			return "Unknown Title";
		}

		// Log the raw JSON input
		System.out.println("[AmazonMusicParser] [DEBUG] Raw JSON for title: " + json);

		// Extract title and artist from the JSON
		String title = extractValue(Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\""), json);
		String artist = extractValue(Pattern.compile("\"artist\"\\s*:\\s*\\{.*?\"name\"\\s*:\\s*\"(.*?)\""), json);
		if (artist == null) {
			artist = extractValue(Pattern.compile("\"artist\"\\s*:\\s*\"(.*?)\""), json);
		}

		// Log extracted values
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted title: " + title);
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted artist: " + artist);

		// Validate and return the formatted title
		if (title == null || artist == null) {
			System.err.println("[AmazonMusicParser] [ERROR] Failed to extract title or artist.");
			return "Unknown Title";
		}

		return artist + " - " + title;
	}

	/**
	 * Parses the audio URL from the JSON string.
	 *
	 * @param json the raw JSON response as a string
	 * @return the audio URL or null if not found or invalid
	 */
	public static String parseAudioUrl(String json) {
		if (json == null || json.isEmpty()) {
			System.err.println("[AmazonMusicParser] [ERROR] JSON is null or empty.");
			return null;
		}

		// Log the raw JSON input
		System.out.println("[AmazonMusicParser] [DEBUG] Raw JSON for audioUrl: " + json);

		// Try to extract audioUrl directly
		String audioUrl = extractValue(AUDIO_URL_PATTERN, json);

		// Log the extracted audioUrl
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted audioUrl: " + audioUrl);

		// If audioUrl is null, try to extract from nested "urls" object
		if (audioUrl == null) {
			System.out.println("[AmazonMusicParser] [DEBUG] Attempting to extract audioUrl from nested 'urls' object.");
			Matcher urlsMatcher = Pattern.compile("\"urls\"\\s*:\\s*\\{(.*?)\\}").matcher(json);
			if (urlsMatcher.find()) {
				String urlsContent = urlsMatcher.group(1);
				audioUrl = extractValue(Pattern.compile("\"high\"\\s*:\\s*\"(.*?)\""), urlsContent);
				if (audioUrl == null) {
					audioUrl = extractValue(Pattern.compile("\"medium\"\\s*:\\s*\"(.*?)\""), urlsContent);
				}
				if (audioUrl == null) {
					audioUrl = extractValue(Pattern.compile("\"low\"\\s*:\\s*\"(.*?)\""), urlsContent);
				}
			}
		}

		// Log the final audioUrl
		System.out.println("[AmazonMusicParser] [DEBUG] Final extracted audioUrl: " + audioUrl);

		// Validate the audio URL format
		if (audioUrl == null || !audioUrl.matches("(?i).+\\.(mp3|m4a|flac|ogg|wav)(\\?.*)?$")) {
			System.err.println("[AmazonMusicParser] [ERROR] Invalid or unsupported audio URL: " + audioUrl);
			return null;
		}

		return audioUrl;
	}

	/**
	 * Parses the ISRC from the JSON string.
	 *
	 * @param json the raw JSON response as a string
	 * @return the ISRC or null if not found
	 */
	public static String parseIsrc(String json) {
		if (json == null || json.isEmpty()) {
			System.err.println("[AmazonMusicParser] [ERROR] JSON is null or empty.");
			return null;
		}

		// Log the raw JSON input
		System.out.println("[AmazonMusicParser] [DEBUG] Raw JSON for ISRC: " + json);

		// Extract ISRC
		String isrc = extractValue(ISRC_PATTERN, json);

		// Log the extracted ISRC
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted ISRC: " + isrc);

		return isrc;
	}

	/**
	 * Parses the artwork URL from the JSON string.
	 *
	 * @param json the raw JSON response as a string
	 * @return the artwork URL or null if not found
	 */
	public static String parseArtworkUrl(String json) {
		if (json == null || json.isEmpty()) {
			System.err.println("[AmazonMusicParser] [ERROR] JSON is null or empty.");
			return null;
		}

		// Log the raw JSON input
		System.out.println("[AmazonMusicParser] [DEBUG] Raw JSON for artworkUrl: " + json);

		// Extract artwork URL
		String artworkUrl = extractValue(ARTWORK_URL_PATTERN, json);

		// Log the extracted artwork URL
		System.out.println("[AmazonMusicParser] [DEBUG] Extracted artworkUrl: " + artworkUrl);

		return artworkUrl;
	}

	/**
	 * Extracts the first group matched by the given regex pattern in the provided text.
	 *
	 * @param pattern the compiled regex pattern
	 * @param text    the text to search
	 * @return the matched group or null if not found
	 */
	private static String extractValue(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}
}
