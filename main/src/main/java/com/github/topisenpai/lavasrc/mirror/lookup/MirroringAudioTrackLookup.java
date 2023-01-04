package com.github.topisenpai.lavasrc.mirror.lookup;

import com.github.topisenpai.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;

import java.util.function.Function;

@FunctionalInterface
public interface MirroringAudioTrackLookup extends Function<MirroringAudioTrack, AudioItem> {
}
