package com.github.topisenpai.lavasrc.plugin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.topisenpai.lavasrc.applemusic.AppleMusicAudioPlaylist;
import com.github.topisenpai.lavasrc.deezer.DeezerAudioPlaylist;
import com.github.topisenpai.lavasrc.spotify.SpotifyAudioPlaylist;
import com.github.topisenpai.lavasrc.yandexmusic.YandexMusicAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier;
import org.springframework.stereotype.Component;

@Component
public class LavaSrcAudioPluginInfoModifier implements AudioPluginInfoModifier {

	@Override
	public void modifyAudioPlaylistPluginInfo(AudioPlaylist playlist, ObjectNode node) {
		if (playlist instanceof SpotifyAudioPlaylist) {
			var spotifyPlaylist = (SpotifyAudioPlaylist) playlist;
			node.set("type", new TextNode(spotifyPlaylist.getType()));
			node.set("identifier", new TextNode(spotifyPlaylist.getIdentifier()));
			node.set("artworkUrl", new TextNode(spotifyPlaylist.getArtworkURL()));
			node.set("author", new TextNode(spotifyPlaylist.getAuthor()));
		} else if (playlist instanceof AppleMusicAudioPlaylist) {
			var appleMusicPlaylist = (AppleMusicAudioPlaylist) playlist;
			node.set("type", new TextNode(appleMusicPlaylist.getType()));
			node.set("identifier", new TextNode(appleMusicPlaylist.getIdentifier()));
			node.set("artworkUrl", new TextNode(appleMusicPlaylist.getArtworkURL()));
			node.set("author", new TextNode(appleMusicPlaylist.getAuthor()));
		} else if (playlist instanceof DeezerAudioPlaylist) {
			var deezerPlaylist = (DeezerAudioPlaylist) playlist;
			node.set("type", new TextNode(deezerPlaylist.getType()));
			node.set("identifier", new TextNode(deezerPlaylist.getIdentifier()));
			node.set("artworkUrl", new TextNode(deezerPlaylist.getArtworkURL()));
			node.set("author", new TextNode(deezerPlaylist.getAuthor()));
		} else if (playlist instanceof YandexMusicAudioPlaylist) {
			var yandexMusicPlaylist = (YandexMusicAudioPlaylist) playlist;
			node.set("type", new TextNode(yandexMusicPlaylist.getType()));
			node.set("identifier", new TextNode(yandexMusicPlaylist.getIdentifier()));
			node.set("artworkUrl", new TextNode(yandexMusicPlaylist.getArtworkURL()));
			node.set("author", new TextNode(yandexMusicPlaylist.getAuthor()));
		}
	}
}
