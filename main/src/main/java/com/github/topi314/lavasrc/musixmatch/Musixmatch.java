
package com.github.topi314.lavasrc.musixmatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Musixmatch {
	private static final int RATE_LIMIT = 1;
	private static final Semaphore rateLimiter = new Semaphore(RATE_LIMIT);
	private static volatile long lastRequestTime = 0L;
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String TOKEN_FILE = "musixmatch_token.json";
	private static final long TOKEN_TTL = 55000L;
	private TokenData tokenData;
	private CompletableFuture<String> tokenPromise;

	public Musixmatch() {
		initializeToken();
	}

	private void initializeToken() {
		try {
			getToken().get();
		} catch (Exception e) {
			logError("Musixmatch initialization failed", e);
		}
	}

	private static class TokenData {
		public String value;
		public long expires;
	}

	private CompletableFuture<TokenData> readTokenFromFile() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String json = Files.readString(Paths.get(TOKEN_FILE));
				return MAPPER.readValue(json, TokenData.class);
			} catch (IOException e) {
				return null;
			}
		});
	}

	private CompletableFuture<Void> saveTokenToFile(String token, long expires) {
		return CompletableFuture.runAsync(() -> {
			try {
				TokenData data = new TokenData();
				data.value = token;
				data.expires = expires;
				String json = MAPPER.writeValueAsString(data);
				Files.writeString(Paths.get(TOKEN_FILE), json);
			} catch (IOException e) {
				logError("Failed to save token to file", e);
			}
		});
	}

	private CompletableFuture<String> fetchToken() {
		return apiGet(Constants.ENDPOINTS.get("TOKEN"))
			.thenApply(data -> {
				Map<String, Object> message = getMap(data, "message");
				Map<String, Object> header = getMap(message, "header");
				if (header == null || !Objects.equals(header.get("status_code"), 200)) {
					Object hint = header != null ? header.get("hint") : null;
					String errorMessage = hint != null ? hint.toString() : "Invalid token response";
					throw new RuntimeException(errorMessage);
				}
				Map<String, Object> body = getMap(message, "body");
				return body != null ? (String) body.get("user_token") : null;
			});
	}

	public CompletableFuture<String> getToken() {
		long now = System.currentTimeMillis();
		if (tokenData == null) {
			return readTokenFromFile().thenCompose(data -> {
				tokenData = data;
				return getToken();
			});
		}
		if (tokenData != null && now < tokenData.expires) {
			return CompletableFuture.completedFuture(tokenData.value);
		}
		if (tokenPromise != null) return tokenPromise;
		tokenPromise = fetchToken().thenCompose(token -> {
			tokenData = new TokenData();
			tokenData.value = token;
			tokenData.expires = now + TOKEN_TTL;
			return saveTokenToFile(token, tokenData.expires).thenApply(v -> token);
		}).whenComplete((r, e) -> tokenPromise = null);
		return tokenPromise;
	}

	public CompletableFuture<Map<String, Object>> apiGet(String url) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				acquireRateLimit();
				HttpClient client = HttpClient.newHttpClient();
				HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) throw new IOException("API request failed: " + response.statusCode());
				return MAPPER.readValue(response.body(), Map.class);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static void acquireRateLimit() {
		synchronized (rateLimiter) {
			long now = System.currentTimeMillis();
			long wait = 1000 - (now - lastRequestTime);
			if (wait > 0) {
				try {
					TimeUnit.MILLISECONDS.sleep(wait);
				} catch (InterruptedException ignored) {}
			}
			lastRequestTime = System.currentTimeMillis();
		}
	}

	public String cleanLyrics(String lyrics) {
		if (lyrics == null) return "";
		lyrics = lyrics.replaceAll(Constants.TIMESTAMPS_REGEX, "");
		String[] lines = lyrics.split("\n");
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			line = line.trim();
			if (!line.matches(Constants.EMPTY_LINES_REGEX)) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString().trim();
	}

	public List<LrcLine> parseSubtitles(String subtitleBody) {
		if (subtitleBody == null) return Collections.emptyList();
		try {
			List<Map<String, Object>> subtitleData = MAPPER.readValue(subtitleBody, List.class);
			List<LrcLine> result = new ArrayList<>();
			for (Map<String, Object> item : subtitleData) {
				Map<String, Object> time = getMap(item, "time");
				double timeValue = time != null && time.get("total") instanceof Number
					? ((Number) time.get("total")).doubleValue()
					: time != null ? Double.parseDouble(time.get("total").toString()) : 0.0;
				result.add(new LrcLine((long) (timeValue * 1000), (String) item.get("text")));
			}
			return result;
		} catch (Exception e) {
			logError("Failed to parse subtitles", e);
			return Collections.emptyList();
		}
	}

	public CompletableFuture<Optional<Map<String, Object>>> searchTrack(String title, String token) {
		String url = Constants.ENDPOINTS.get("SEARCH") + "&q_track=" + encodeURIComponent(title) + "&usertoken=" + token;
		return apiGet(url).thenApply(data -> {
			Map<String, Object> message = getMap(data, "message");
			Map<String, Object> body = getMap(message, "body");
			List<Map<String, Object>> trackList = getList(body, "track_list");
			if (trackList != null && !trackList.isEmpty()) {
				Map<String, Object> trackObj = trackList.get(0);
				return Optional.ofNullable(getMap(trackObj, "track"));
			}
			return Optional.empty();
		});
	}

	public CompletableFuture<Map<String, Object>> getAltLyrics(String title, String artist, String token) {
		String url = Constants.ENDPOINTS.get("ALT_LYRICS") + "&usertoken=" + token + "&q_artist=" + encodeURIComponent(artist) + "&q_track=" + encodeURIComponent(title);
		return apiGet(url).thenApply(data -> {
			Map<String, Object> message = getMap(data, "message");
			Map<String, Object> body = getMap(message, "body");
			Map<String, Object> calls = getMap(body, "macro_calls");
			Map<String, Object> result = new HashMap<>();

			Map<String, Object> lyricsCall = getMap(calls, "track.lyrics.get");
			Map<String, Object> lyricsMessage = getMap(lyricsCall, "message");
			Map<String, Object> lyricsBody = getMap(lyricsMessage, "body");
			String lyrics = lyricsBody != null ? (String) lyricsBody.get("lyrics_body") : null;
			result.put("lyrics", lyrics);

			Map<String, Object> trackCall = getMap(calls, "matcher.track.get");
			Map<String, Object> trackMessage = getMap(trackCall, "message");
			Map<String, Object> trackBody = getMap(trackMessage, "body");
			Map<String, Object> track = trackBody != null ? getMap(trackBody, "track") : null;
			result.put("track", track);

			Map<String, Object> subtitlesCall = getMap(calls, "track.subtitles.get");
			Map<String, Object> subtitlesMessage = getMap(subtitlesCall, "message");
			Map<String, Object> subtitlesBody = getMap(subtitlesMessage, "body");
			if (subtitlesBody != null) {
				List<Map<String, Object>> subtitleList = getList(subtitlesBody, "subtitle_list");
				if (subtitleList != null && !subtitleList.isEmpty()) {
					Map<String, Object> subtitle = getMap(subtitleList.get(0), "subtitle");
					String subtitleBody = subtitle != null ? (String) subtitle.get("subtitle_body") : null;
					result.put("subtitles", subtitleBody);
				} else {
					result.put("subtitles", null);
				}
			} else {
				result.put("subtitles", null);
			}
			return result;
		});
	}

	public ParsedQuery parseQuery(String query) {
		String cleanedQuery = query.replaceAll("\\b(VEVO|Official Music Video|Lyrics)\\b", "").trim();
		Pattern pattern = Pattern.compile(Constants.ARTIST_TITLE_REGEX);
		Matcher matcher = pattern.matcher(cleanedQuery);
		if (matcher.find()) {
			return new ParsedQuery(matcher.group(1).trim(), matcher.group(2).trim());
		}
		int lastSpaceIndex = cleanedQuery.lastIndexOf(' ');
		if (lastSpaceIndex > 0) {
			return new ParsedQuery(cleanedQuery.substring(0, lastSpaceIndex).trim(), cleanedQuery.substring(lastSpaceIndex + 1).trim());
		}
		return new ParsedQuery(null, cleanedQuery);
	}

	public CompletableFuture<Result> findLyrics(String query) {
		return getToken().thenCompose(token -> {
			ParsedQuery parsed = parseQuery(query);
			if (parsed.artist != null) {
				return getAltLyrics(parsed.title, parsed.artist, token).thenCompose(altResult -> {
					if (altResult.get("subtitles") != null || altResult.get("lyrics") != null) {
						return CompletableFuture.completedFuture(formatResult((String) altResult.get("subtitles"), (String) altResult.get("lyrics"), getMap(altResult, "track")));
					}
					return searchTrack(query, token).thenCompose(trackResultOpt -> {
						if (trackResultOpt.isPresent()) {
							Map<String, Object> trackResult = trackResultOpt.get();
							return getLyricsFromTrack(trackResult, token).thenApply(lyricsData -> {
								if (lyricsData != null && (lyricsData.get("subtitles") != null || lyricsData.get("lyrics") != null)) {
									return formatResult((String) lyricsData.get("subtitles"), (String) lyricsData.get("lyrics"), trackResult);
								}
								return null;
							});
						}
						return CompletableFuture.completedFuture(null);
					});
				});
			}
			return searchTrack(query, token).thenCompose(trackResultOpt -> {
				if (trackResultOpt.isPresent()) {
					Map<String, Object> trackResult = trackResultOpt.get();
					return getLyricsFromTrack(trackResult, token).thenApply(lyricsData -> {
						if (lyricsData != null && (lyricsData.get("subtitles") != null || lyricsData.get("lyrics") != null)) {
							return formatResult((String) lyricsData.get("subtitles"), (String) lyricsData.get("lyrics"), trackResult);
						}
						return null;
					});
				}
				return getAltLyrics(parsed.title, "", token).thenApply(titleOnlyResult -> {
					if (titleOnlyResult.get("subtitles") != null || titleOnlyResult.get("lyrics") != null) {
						return formatResult((String) titleOnlyResult.get("subtitles"), (String) titleOnlyResult.get("lyrics"), getMap(titleOnlyResult, "track"));
					}
					return null;
				});
			});
		});
	}

	public CompletableFuture<Map<String, Object>> getLyricsFromTrack(Map<String, Object> trackData, String token) {
		String url = Constants.ENDPOINTS.get("LYRICS") + "&track_id=" + trackData.get("track_id") + "&usertoken=" + token;
		return apiGet(url).thenApply(data -> {
			Map<String, Object> message = getMap(data, "message");
			Map<String, Object> body = getMap(message, "body");
			String subtitles = null;
			if (body != null && body.get("subtitle") != null) {
				Map<String, Object> subtitle = getMap(body, "subtitle");
				subtitles = subtitle != null ? (String) subtitle.get("subtitle_body") : null;
			}
			Map<String, Object> result = new HashMap<>();
			result.put("subtitles", subtitles);
			result.put("lyrics", subtitles != null ? cleanLyrics(subtitles) : null);
			return result;
		});
	}

	public Result formatResult(String subtitles, String lyrics, Map<String, Object> trackData) {
		List<LrcLine> lines = subtitles != null ? parseSubtitles(subtitles) : Collections.emptyList();
		return new Result(
			lyrics,
			lines,
			trackData != null ? new TrackInfo(
				(String) trackData.get("track_name"),
				(String) trackData.get("artist_name"),
				(String) trackData.get("album_coverart_350x350")
			) : null,
			"Musixmatch"
		);
	}

	public CompletableFuture<LrcResult> getLrc(String query) {
		return findLyrics(query).thenApply(result -> {
			if (result == null) return null;
			String synced = result.lines != null ? result.lines.stream().map(l -> l.line).collect(Collectors.joining("\n")) : null;
			return new LrcResult(synced, result.text, result.track);
		});
	}

	private static String encodeURIComponent(String s) {
		try {
			return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.toString());
		} catch (Exception e) {
			return s;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> getMap(Map<String, Object> map, String key) {
		Object value = map != null ? map.get(key) : null;
		return value instanceof Map ? (Map<String, Object>) value : null;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> getList(Map<String, Object> map, String key) {
		Object value = map != null ? map.get(key) : null;
		return value instanceof List ? (List<Map<String, Object>>) value : null;
	}

	private static void logError(String message, Exception e) {
		System.err.println(message + ": " + (e != null ? e.getMessage() : "unknown error"));
	}

	public static class LrcLine {
		public final long start;
		public final String line;
		public LrcLine(long start, String line) {
			this.start = start;
			this.line = line;
		}
	}

	public static class ParsedQuery {
		public final String artist;
		public final String title;
		public ParsedQuery(String artist, String title) {
			this.artist = artist;
			this.title = title;
		}
	}

	public static class TrackInfo {
		public final String title;
		public final String author;
		public final String albumArt;
		public TrackInfo(String title, String author, String albumArt) {
			this.title = title;
			this.author = author;
			this.albumArt = albumArt;
		}
	}

	public static class Result {
		public final String text;
		public final List<LrcLine> lines;
		public final TrackInfo track;
		public final String source;
		public Result(String text, List<LrcLine> lines, TrackInfo track, String source) {
			this.text = text;
			this.lines = lines;
			this.track = track;
			this.source = source;
		}
	}

	public static class LrcResult {
		public final String synced;
		public final String unsynced;
		public final TrackInfo track;
		public LrcResult(String synced, String unsynced, TrackInfo track) {
			this.synced = synced;
			this.unsynced = unsynced;
			this.track = track;
		}
	}
}
