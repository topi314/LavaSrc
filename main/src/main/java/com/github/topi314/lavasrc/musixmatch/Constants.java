package com.github.topi314.lavasrc.musixmatch;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Constants {
    private Constants() {}

    public static final Map<String, String> ENDPOINTS;
    static {
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("TOKEN", "https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0");
        endpoints.put("SEARCH", "https://apic-desktop.musixmatch.com/ws/1.1/track.search?app_id=web-desktop-app-v1.0&page_size=3&page=1&s_track_rating=desc");
        endpoints.put("LYRICS", "https://apic-desktop.musixmatch.com/ws/1.1/track.subtitle.get?app_id=web-desktop-app-v1.0&subtitle_format=lrc");
        endpoints.put("ALT_LYRICS", "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?format=json&namespace=lyrics_richsynched&subtitle_format=mxm&app_id=web-desktop-app-v1.0");
        ENDPOINTS = Collections.unmodifiableMap(endpoints);
    }

    public static final class Regex {
        private Regex() {}
        public static final String TIMESTAMPS = "\\[\\d+:\\d+\\.\\d+\\]";
        public static final String EMPTY_LINES = "^\\s*$";
        public static final String ARTIST_TITLE = "^(.*?)\\s*[-–~]\\s*(.+)$";
    }
}
