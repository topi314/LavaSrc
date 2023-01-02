package com.github.topisenpai.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;

import java.util.function.BiFunction;

@FunctionalInterface
public interface DelegatedAudioLookup extends BiFunction<MirroringAudioSourceManager, MirroringAudioTrack, AudioItem> {
}
