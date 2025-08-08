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
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Musixmatch {
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final String TOKEN_FILE = "musixmatch_token.json";
	private static final long TOKEN_TTL = 55000L;
	private static final int MAX_REQUESTS_PER_MINUTE = 30;
	private static final long MIN_REQUEST_INTERVAL = 1000; 
	private static final int MAX_CONCURRENT_REQUESTS = 3; 
	private TokenData tokenData;
	private CompletableFuture<String> tokenPromise;
	private final Semaphore requestSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);
	private final Queue<Long> requestTimestamps = new ConcurrentLinkedQueue<>();
	private volatile long lastRequestTime = 0;
	private final Object rateLimitLock = new Object();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	public Musixmatch() {
		initializeToken();
		startCleanupTask();
	}

	private void startCleanupTask() {
		scheduler.scheduleAtFixedRate(() -> {
			long oneMinuteAgo = System.currentTimeMillis() - 60000;
			requestTimestamps.removeIf(timestamp -> timestamp < oneMinuteAgo);
		}, 1, 1, TimeUnit.MINUTES);
	}

	private void initializeToken() {
		try {
			getToken().get();
		} catch (Exception e) {
			System.err.println("Musixmatch initialization failed: " + e.getMessage());
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
				return mapper.readValue(json, TokenData.class);
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
				String json = mapper.writeValueAsString(data);
				Files.writeString(Paths.get(TOKEN_FILE), json);
			} catch (IOException e) {
				System.err.println("Failed to save token to file: " + e.getMessage());
			}
		});
	}

	private CompletableFuture<String> fetchToken() {
		return rateLimitedApiGet(Constants.ENDPOINTS.get("TOKEN"))
			.thenApply(data -> {
				@SuppressWarnings("unchecked")
				Map<String, Object> message = (Map<String, Object>) data.get("message");
				@SuppressWarnings("unchecked")
				Map<String, Object> header = (Map<String, Object>) message.get("header");
				if ((Integer) header.get("status_code") != 200) {
					Object hint = header.get("hint");
					String errorMessage = hint != null ? hint.toString() : "Invalid token response";
					throw new RuntimeException(errorMessage);
				}
				@SuppressWarnings("unchecked")
				Map<String, Object> body = (Map<String, Object>) message.get("body");
				return (String) body.get("user_token");
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

	private CompletableFuture<Map<String, Object>> rateLimitedApiGet(String url) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				requestSemaphore.acquire();
				
				try {
					enforceRateLimit();
					
					return makeHttpRequest(url);
				} finally {
					requestSemaphore.release();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Request interrupted", e);
			} catch (Exception e) {
				throw new RuntimeException("Rate-limited API request failed", e);
			}
		});
	}

	private void enforceRateLimit() throws InterruptedException {
		synchronized (rateLimitLock) {
			long now = System.currentTimeMillis();
			
			long oneMinuteAgo = now - 60000;
			requestTimestamps.removeIf(timestamp -> timestamp < oneMinuteAgo);
			
			if (requestTimestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
				long oldestRequest = requestTimestamps.peek();
				if (oldestRequest != null) {
					long waitTime = 60000 - (now - oldestRequest) + 100; 
					if (waitTime > 0) {
						System.out.println("Rate limit reached, waiting " + waitTime + "ms");
						Thread.sleep(waitTime);
						now = System.currentTimeMillis();
					}
				}
			}
			
			long timeSinceLastRequest = now - lastRequestTime;
			if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
				long waitTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest;
				System.out.println("Enforcing request interval, waiting " + waitTime + "ms");
				Thread.sleep(waitTime);
				now = System.currentTimeMillis();
			}
			
			requestTimestamps.offer(now);
			lastRequestTime = now;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> makeHttpRequest(String url) throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(30))
			.build();
		
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		
		if (response.statusCode() == 429) {
			String retryAfter = response.headers().firstValue("Retry-After").orElse("60");
			long waitSeconds = Long.parseLong(retryAfter);
			System.out.println("Server rate limit hit, waiting " + waitSeconds + " seconds");
			Thread.sleep(waitSeconds * 1000);
			
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
		}
		
		if (response.statusCode() != 200) {
			throw new IOException("API request failed: " + response.statusCode());
		}
		
		return (Map<String, Object>) mapper.readValue(response.body(), Map.class);
	}

	@SuppressWarnings("unchecked")
	public CompletableFuture<Map<String, Object>> apiGet(String url) {
		return rateLimitedApiGet(url);
	}

	public String cleanLyrics(String lyrics) {
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

	@SuppressWarnings("unchecked")
	public List<LrcLine> parseSubtitles(String subtitleBody) {
		try {
			List<Map<String, Object>> subtitleData = (List<Map<String, Object>>) mapper.readValue(subtitleBody, List.class);
			List<LrcLine> result = new ArrayList<>();
			for (Map<String, Object> item : subtitleData) {
				Map<String, Object> time = (Map<String, Object>) item.get("time");
				double timeValue = time.get("total") instanceof Number
					? ((Number) time.get("total")).doubleValue()
					: Double.parseDouble(time.get("total").toString());
				result.add(new LrcLine((long) (timeValue * 1000), (String) item.get("text")));
			}
			return result;
		} catch (Exception e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public CompletableFuture<Map<String, Object>> searchTrack(String title, String token) {
		String url = Constants.ENDPOINTS.get("SEARCH") + "&q_track=" + encodeURIComponent(title) + "&usertoken=" + token;
		return apiGet(url).thenApply(data -> {
			Map<String, Object> message = (Map<String, Object>) data.get("message");
			Map<String, Object> body = (Map<String, Object>) message.get("body");
			List<Map<String, Object>> trackList = (List<Map<String, Object>>) body.get("track_list");
			if (trackList != null && !trackList.isEmpty()) {
				Map<String, Object> trackObj = trackList.get(0);
				return (Map<String, Object>) trackObj.get("track");
			}
			return null;
		});
	}

	@SuppressWarnings("unchecked")
	public CompletableFuture<Map<String, Object>> getAltLyrics(String title, String artist, String token) {
		String url = Constants.ENDPOINTS.get("ALT_LYRICS") + "&usertoken=" + token + "&q_artist=" + encodeURIComponent(artist) + "&q_track=" + encodeURIComponent(title);
		return apiGet(url).thenApply(data -> {
			Map<String, Object> message = (Map<String, Object>) data.get("message");
			Map<String, Object> body = (Map<String, Object>) message.get("body");
			Map<String, Object> calls = (Map<String, Object>) body.get("macro_calls");
			Map<String, Object> result = new HashMap<>();
			
			Map<String, Object> lyricsCall = (Map<String, Object>) calls.get("track.lyrics.get");
			Map<String, Object> lyricsMessage = (Map<String, Object>) lyricsCall.get("message");
			Map<String, Object> lyricsBody = (Map<String, Object>) lyricsMessage.get("body");
			String lyrics = lyricsBody != null ? (String) lyricsBody.get("lyrics_body") : null;
			result.put("lyrics", lyrics);
			
			Map<String, Object> trackCall = (Map<String, Object>) calls.get("matcher.track.get");
			Map<String, Object> trackMessage = (Map<String, Object>) trackCall.get("message");
			Map<String, Object> trackBody = (Map<String, Object>) trackMessage.get("body");
			Map<String, Object> track = trackBody != null ? (Map<String, Object>) trackBody.get("track") : null;
			result.put("track", track);
			
			Map<String, Object> subtitlesCall = (Map<String, Object>) calls.get("track.subtitles.get");
			Map<String, Object> subtitlesMessage = (Map<String, Object>) subtitlesCall.get("message");
			Map<String, Object> subtitlesBody = (Map<String, Object>) subtitlesMessage.get("body");
			if (subtitlesBody != null) {
				List<Map<String, Object>> subtitleList = (List<Map<String, Object>>) subtitlesBody.get("subtitle_list");
				if (subtitleList != null && !subtitleList.isEmpty()) {
					Map<String, Object> subtitle = (Map<String, Object>) subtitleList.get(0).get("subtitle");
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

	@SuppressWarnings("unchecked")
	public CompletableFuture<Result> findLyrics(String query) {
		return getToken().thenCompose(token -> {
			ParsedQuery parsed = parseQuery(query);
			if (parsed.artist != null) {
				return getAltLyrics(parsed.title, parsed.artist, token).thenCompose(altResult -> {
					if (altResult.get("subtitles") != null || altResult.get("lyrics") != null) {
						return CompletableFuture.completedFuture(formatResult((String) altResult.get("subtitles"), (String) altResult.get("lyrics"), (Map<String, Object>) altResult.get("track")));
					}
					return searchTrack(query, token).thenCompose(trackResult -> {
						if (trackResult != null) {
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
			return searchTrack(query, token).thenCompose(trackResult -> {
				if (trackResult != null) {
					return getLyricsFromTrack(trackResult, token).thenApply(lyricsData -> {
						if (lyricsData != null && (lyricsData.get("subtitles") != null || lyricsData.get("lyrics") != null)) {
							return formatResult((String) lyricsData.get("subtitles"), (String) lyricsData.get("lyrics"), trackResult);
						}
						return null;
					});
				}
				return getAltLyrics(parsed.title, "", token).thenApply(titleOnlyResult -> {
					if (titleOnlyResult.get("subtitles") != null || titleOnlyResult.get("lyrics") != null) {
						return formatResult((String) titleOnlyResult.get("subtitles"), (String) titleOnlyResult.get("lyrics"), (Map<String, Object>) titleOnlyResult.get("track"));
					}
					return null;
				});
			});
		});
	}

	@SuppressWarnings("unchecked")
	public CompletableFuture<Map<String, Object>> getLyricsFromTrack(Map<String, Object> trackData, String token) {
		String url = Constants.ENDPOINTS.get("LYRICS") + "&track_id=" + trackData.get("track_id") + "&usertoken=" + token;
		return apiGet(url).thenApply(data -> {
			Map<String, Object> message = (Map<String, Object>) data.get("message");
			Map<String, Object> body = (Map<String, Object>) message.get("body");
			String subtitles = null;
			if (body.get("subtitle") != null) {
				Map<String, Object> subtitle = (Map<String, Object>) body.get("subtitle");
				subtitles = (String) subtitle.get("subtitle_body");
			}
			Map<String, Object> result = new HashMap<>();
			result.put("subtitles", subtitles);
			result.put("lyrics", subtitles != null ? cleanLyrics(subtitles) : null);
			return result;
		});
	}

	public Result formatResult(String subtitles, String lyrics, Map<String, Object> trackData) {
		List<LrcLine> lines = subtitles != null ? parseSubtitles(subtitles) : null;
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
			String synced = result.lines != null ? String.join("\n", result.lines.stream().map(l -> l.line).collect(Collectors.toList())) : null;
			return new LrcResult(synced, result.text, result.track);
		});
	}

	public RateLimitStatus getRateLimitStatus() {
		synchronized (rateLimitLock) {
			long now = System.currentTimeMillis();
			long oneMinuteAgo = now - 60000;
			requestTimestamps.removeIf(timestamp -> timestamp < oneMinuteAgo);
			
			int requestsInLastMinute = requestTimestamps.size();
			int remainingRequests = Math.max(0, MAX_REQUESTS_PER_MINUTE - requestsInLastMinute);
			long timeSinceLastRequest = now - lastRequestTime;
			long timeUntilNextRequest = Math.max(0, MIN_REQUEST_INTERVAL - timeSinceLastRequest);
			
			return new RateLimitStatus(
				requestsInLastMinute,
				remainingRequests,
				MAX_REQUESTS_PER_MINUTE,
				timeUntilNextRequest,
				requestSemaphore.availablePermits(),
				MAX_CONCURRENT_REQUESTS
			);
		}
	}

	public void shutdown() {
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private static String encodeURIComponent(String s) {
		try {
			return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.toString());
		} catch (Exception e) {
			return s;
		}
	}

	public static class LrcLine {
		public long start;
		public String line;
		public LrcLine(long start, String line) {
			this.start = start;
			this.line = line;
		}
	}

	public static class ParsedQuery {
		public String artist;
		public String title;
		public ParsedQuery(String artist, String title) {
			this.artist = artist;
			this.title = title;
		}
	}

	public static class TrackInfo {
		public String title;
		public String author;
		public String albumArt;
		public TrackInfo(String title, String author, String albumArt) {
			this.title = title;
			this.author = author;
			this.albumArt = albumArt;
		}
	}

	public static class Result {
		public String text;
		public List<LrcLine> lines;
		public TrackInfo track;
		public String source;
		public Result(String text, List<LrcLine> lines, TrackInfo track, String source) {
			this.text = text;
			this.lines = lines;
			this.track = track;
			this.source = source;
		}
	}

	public static class LrcResult {
		public String synced;
		public String unsynced;
		public TrackInfo track;
		public LrcResult(String synced, String unsynced, TrackInfo track) {
			this.synced = synced;
			this.unsynced = unsynced;
			this.track = track;
		}
	}

	public static class RateLimitStatus {
		public int requestsInLastMinute;
		public int remainingRequests;
		public int maxRequestsPerMinute;
		public long timeUntilNextRequest;
		public int availableConcurrentSlots;
		public int maxConcurrentRequests;
		
		public RateLimitStatus(int requestsInLastMinute, int remainingRequests, int maxRequestsPerMinute,
							   long timeUntilNextRequest, int availableConcurrentSlots, int maxConcurrentRequests) {
			this.requestsInLastMinute = requestsInLastMinute;
			this.remainingRequests = remainingRequests;
			this.maxRequestsPerMinute = maxRequestsPerMinute;
			this.timeUntilNextRequest = timeUntilNextRequest;
			this.availableConcurrentSlots = availableConcurrentSlots;
			this.maxConcurrentRequests = maxConcurrentRequests;
		}
		
		@Override
		public String toString() {
			return String.format("Rate Limit Status: %d/%d requests used, %d remaining, next request in %dms, %d/%d concurrent slots available",
				requestsInLastMinute, maxRequestsPerMinute, remainingRequests, timeUntilNextRequest,
				availableConcurrentSlots, maxConcurrentRequests);
		}
	}
}
