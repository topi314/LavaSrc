package com.github.topi314.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

public class MirroringException extends FriendlyException {

	public MirroringException() {
		super("Unable to find this track with a different source!", Severity.COMMON, new Exception());
	}

}
