package com.github.topi314.lavasrc.musixmatch;

import java.util.Map;

public class Constants {
    public static final Map<String, String> ENDPOINTS = Map.of(
        "TOKEN", "https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0",
        "SEARCH", "https://apic-desktop.musixmatch.com/ws/1.1/track.search?app_id=web-desktop-app-v1.0&page_size=3&page=1&s_track_rating=desc",
        "LYRICS", "https://apic-desktop.musixmatch.com/ws/1.1/track.subtitle.get?app_id=web-desktop-app-v1.0&subtitle_format=lrc",
        "ALT_LYRICS", "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?format=json&namespace=lyrics_richsynched&subtitle_format=mxm&app_id=web-desktop-app-v1.0"
    );

    public static final String TIMESTAMPS_REGEX = "\\[\\d+:\\d+\\.\\d+\\]";
    public static final String EMPTY_LINES_REGEX = "^\\s*$";
    public static final String ARTIST_TITLE_REGEX = "^(.*?)\\s*[-–~]\\s*(.+)$";
}
