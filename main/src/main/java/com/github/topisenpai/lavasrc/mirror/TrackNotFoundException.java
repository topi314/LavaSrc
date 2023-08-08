package com.github.topisenpai.lavasrc.mirror;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

public class TrackNotFoundException extends FriendlyException {

	private static final long serialVersionUID = 6550093849278285754L;

	public TrackNotFoundException() {
		super("Playlist is empty", FriendlyException.Severity.COMMON, null);
	}

}
