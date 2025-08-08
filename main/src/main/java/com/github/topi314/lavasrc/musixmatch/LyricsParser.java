package com.github.topi314.lavasrc.musixmatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class LyricsParser {
    public static String cleanLyrics(String lyrics) {
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

    public static List<Musixmatch.LrcLine> parseSubtitles(String subtitleBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> subtitleData = mapper.readValue(subtitleBody, List.class);
            List<Musixmatch.LrcLine> result = new ArrayList<>();
            for (Map<String, Object> item : subtitleData) {
                double time = ((Map<String, Object>) item.get("time")).get("total") instanceof Number
                    ? ((Number) ((Map<String, Object>) item.get("time")).get("total")).doubleValue()
                    : Double.parseDouble(((Map<String, Object>) item.get("time")).get("total").toString());
                result.add(new Musixmatch.LrcLine((long) (time * 1000), (String) item.get("text")));
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
