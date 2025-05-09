package com.github.topi314.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import org.jetbrains.annotations.NotNull;

public interface MirroringAudioSourceManager extends AudioSourceManager {

	@NotNull
	MirrorResolver getMirrorResolver();

}
