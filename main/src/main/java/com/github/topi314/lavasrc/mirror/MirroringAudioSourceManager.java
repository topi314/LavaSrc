package com.github.topi314.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;

public interface MirroringAudioSourceManager extends AudioSourceManager {

	MirrorResolver getMirrorResolver();

}
