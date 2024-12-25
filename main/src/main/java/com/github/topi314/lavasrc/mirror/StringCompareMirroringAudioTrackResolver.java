package com.github.topi314.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Pair;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StringCompareMirroringAudioTrackResolver {
	private static final Logger log = LoggerFactory.getLogger(StringCompareMirroringAudioTrackResolver.class);
	private final boolean enabled;
	private final String[] sources;
	private final Float titleThreshold;
	private final Float authorThreshold;
	private final Float totalMatchThreshold;
	private final boolean skipSoundCloudGo;
	private final Float levelOnePenalty;
	private final Float levelTwoPenalty;
	private final Float levelThreePenalty;

	public StringCompareMirroringAudioTrackResolver(
		boolean enabled,
		String[] sources,
		Float titleThreshold,
		Float authorThreshold,
		Float totalMatchThreshold,
		boolean skipSoundCloudGo,
		Float levelOnePenalty,
		Float levelTwoPenalty,
		Float levelThreePenalty
	) {
		this.enabled = enabled;
		this.sources = sources;
		this.titleThreshold = titleThreshold;
		this.authorThreshold = authorThreshold;
		this.totalMatchThreshold = totalMatchThreshold;
		this.skipSoundCloudGo = skipSoundCloudGo;
		this.levelOnePenalty = levelOnePenalty;
		this.levelTwoPenalty = levelTwoPenalty;
		this.levelThreePenalty = levelThreePenalty;
	}

	public int calculateMatchScore(String targetTitle, String targetAuthor, String candidateTitle, String candidateAuthor,
	                               long targetDuration, long candidateDuration) {

		targetTitle = normalizeString(targetTitle);
		targetAuthor = normalizeString(targetAuthor);
		candidateTitle = normalizeString(candidateTitle);
		candidateAuthor = normalizeString(candidateAuthor);

		int titleScore = FuzzySearch.tokenSortRatio(targetTitle, candidateTitle);
		int titleMatchRatio = FuzzySearch.partialRatio(targetTitle, candidateTitle);
		int authorScore = matchAuthors(targetAuthor, candidateAuthor);
		int durationScore = calculateDurationScore(targetDuration, candidateDuration);

		int totalScore = titleScore + authorScore + durationScore;

		if (titleScore < this.titleThreshold) {
			return 0;
		}

		float penalty = calculatePenalty(titleMatchRatio);
		totalScore = Math.max(0, (int) (totalScore - penalty));

		log.info("Track Scored: {} : {} with score : {} :: Title Score : {} :: Title Match Ratio : {} :: Author Score : {} :: Duration Score : {} :: Penalty : {}",
			candidateTitle, candidateAuthor, totalScore, titleScore, titleMatchRatio, authorScore, durationScore, penalty);

		return adjustFinalScore(totalScore, authorScore);
	}

	private String normalizeString(String input) {
		return input.toLowerCase().trim();
	}

	private int calculateDurationScore(long targetDuration, long candidateDuration) {
		return 100 - (int) Math.abs(targetDuration / 1000L - candidateDuration / 1000L);
	}

	private float calculatePenalty(int titleMatchRatio) {
		int penaltyThreshold = 100;
		if (titleMatchRatio >= 80) {
			return this.levelOnePenalty != null ? this.levelOnePenalty * (penaltyThreshold - titleMatchRatio) : 0.0F;
		} else if (titleMatchRatio >= 65) {
			log.debug("Title Match Ratio is between 65 and 80, applying penalty");
			return this.levelTwoPenalty != null ? this.levelTwoPenalty * (penaltyThreshold - titleMatchRatio) : 0.0F;
		} else {
			log.debug("Title Match Ratio is less than 65, applying penalty");
			return this.levelThreePenalty != null ? this.levelThreePenalty * (penaltyThreshold - titleMatchRatio) : 0.0F;
		}
	}

	private int adjustFinalScore(int totalScore, int authorScore) {
		if (totalScore < this.totalMatchThreshold.intValue() - 1 || totalScore > this.totalMatchThreshold.intValue() + 40) {
			return totalScore;
		} else {
			return authorScore >= this.authorThreshold ? totalScore : this.totalMatchThreshold.intValue() - 2;
		}
	}

	public static int matchAuthors(String targetAuthor, String candidateAuthor) {
		String[] targetAuthors = targetAuthor.split(",\\s*");
		String[] candidateAuthors = candidateAuthor.split(",\\s*");
		int bestScore = 0;

		for (String tAuthor : targetAuthors) {
			for (String cAuthor : candidateAuthors) {
				int score = FuzzySearch.tokenSortRatio(tAuthor.trim(), cAuthor.trim());
				bestScore = Math.max(bestScore, score);
			}
		}

		return bestScore;
	}

	public AudioItem apply(AudioItem item, MirroringAudioTrack track, String provider) {
		if (!this.enabled) {
			return item;
		}

		if (!(item instanceof AudioPlaylist)) {
			log.warn("Item is not an AudioPlaylist but a single track. Skipping advanced mirroring logic.");
			return item;
		}

		AudioPlaylist playlist = (AudioPlaylist) item;

		List<AudioTrack> tracks = new ArrayList<>(playlist.getTracks());
		int initialTrackCount = tracks.size();

		if (this.skipSoundCloudGo) {
			log.debug("Skipping SoundCloud Go tracks");
			tracks = tracks.stream()
				.filter(t -> !isSoundCloudGoTrack(t))
				.collect(Collectors.toList());
			log.debug("Skipped {} SoundCloud Go tracks", initialTrackCount - tracks.size());
		}

		if (isSupportedProvider(provider)) {
			log.info("Applying Advanced Mirroring for " + provider);
			List<Pair<AudioTrack, Integer>> scoredTracks = scoreTracks(tracks, track);

			if (scoredTracks.isEmpty() || getMaxScore(scoredTracks) < this.totalMatchThreshold.intValue()) {
				log.debug("No match found for track: max score is below threshold.");
				return AudioReference.NO_TRACK;
			}

			List<AudioTrack> matchedTracks = scoredTracks.stream()
				.map(Pair::getFirst)
				.collect(Collectors.toList());

			return new BasicAudioPlaylist("Mirroring Search: " + provider, matchedTracks, null, true);
		}

		return item;
	}

	private boolean isSoundCloudGoTrack(AudioTrack track) {
		return track.getSourceManager().getSourceName().equals("soundcloud") && track.getIdentifier().contains("/preview/");
	}

	private boolean isSupportedProvider(String provider) {
		String[] sourcePrefix = provider.split(":");
		return sourcePrefix.length > 0 && Arrays.asList(this.sources).contains(sourcePrefix[0]);
	}
	private List<Pair<AudioTrack, Integer>> scoreTracks(List<AudioTrack> tracks, MirroringAudioTrack track) {
		List<Pair<AudioTrack, Integer>> scoredTracks = new ArrayList<>();
		for (AudioTrack t : tracks) {
			int score = this.calculateMatchScore(
				track.getInfo().title,
				track.getInfo().author,
				t.getInfo().title,
				t.getInfo().author,
				track.getInfo().length,
				t.getInfo().length
			);
			scoredTracks.add(new Pair<>(t, score));
		}
		return scoredTracks;
	}

	private int getMaxScore(List<Pair<AudioTrack, Integer>> scoredTracks) {
		return scoredTracks.stream()
			.map(Pair::getSecond)
			.max(Integer::compareTo)
			.orElse(0);
	}


}
