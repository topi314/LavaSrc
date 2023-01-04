package com.github.topisenpai.lavasrc.mirror.lookup;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioSourceManager;
import com.github.topisenpai.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class MirroringAudioTrackLookupUtil {

    private static final Logger log = LoggerFactory.getLogger(MirroringAudioTrackLookupUtil.class);

    public static String getTrackTitle(MirroringAudioTrack mirroringAudioTrack) {
        var query = mirroringAudioTrack.getInfo().title;
        if (!mirroringAudioTrack.getInfo().author.equals("unknown")) {
            query += " " + mirroringAudioTrack.getInfo().author;
        }
        return query;
    }

    public static AudioItem loadItem(String query, MirroringAudioSourceManager sourceManager) {
        var cf = new CompletableFuture<AudioItem>();
        sourceManager.getAudioPlayerManager().loadItem(query, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(AudioTrack track) {
                log.debug("Track loaded: " + track.getIdentifier());
                cf.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                log.debug("Playlist loaded: " + playlist.getName());
                cf.complete(playlist);
            }

            @Override
            public void noMatches() {
                log.debug("No matches found for: " + query);
                cf.complete(AudioReference.NO_TRACK);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                log.debug("Failed to load: " + query);
                cf.completeExceptionally(exception);
            }
        });
        return cf.join();
    }

}
