package com.github.topi314.lavasrc.musixmatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class LyricsParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String cleanLyrics(String lyrics) {
        if (lyrics == null || lyrics.isEmpty()) return "";
        lyrics = lyrics.replaceAll(Constants.TIMESTAMPS_REGEX, "");
        StringBuilder sb = new StringBuilder();
        for (String line : lyrics.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.matches(Constants.EMPTY_LINES_REGEX)) {
                sb.append(trimmed).append('\n');
            }
        }
        return sb.toString().trim();
    }

    public static List<Musixmatch.LrcLine> parseSubtitles(String subtitleBody) {
        if (subtitleBody == null || subtitleBody.isEmpty()) return Collections.emptyList();
        try {
            List<?> subtitleData = MAPPER.readValue(subtitleBody, List.class);
            List<Musixmatch.LrcLine> result = new ArrayList<>();
            for (Object obj : subtitleData) {
                if (!(obj instanceof Map)) continue;
                Map<?, ?> item = (Map<?, ?>) obj;
                Object timeObj = item.get("time");
                if (!(timeObj instanceof Map)) continue;
                Map<?, ?> timeMap = (Map<?, ?>) timeObj;
                Object totalObj = timeMap.get("total");
                double timeValue;
                if (totalObj instanceof Number) {
                    timeValue = ((Number) totalObj).doubleValue();
                } else {
                    try {
                        timeValue = Double.parseDouble(totalObj.toString());
                    } catch (Exception e) {
                        timeValue = 0.0;
                    }
                }
                String text = item.get("text") != null ? item.get("text").toString() : "";
                result.add(new Musixmatch.LrcLine((long) (timeValue * 1000), text));
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
