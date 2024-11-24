package com.github.topi314.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import java.util.function.Function;

@FunctionalInterface
public interface MirroringAudioTrackResolver
        extends Function<MirroringAudioTrack, AudioItem> {}
