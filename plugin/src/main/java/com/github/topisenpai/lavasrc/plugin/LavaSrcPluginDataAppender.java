package com.github.topisenpai.lavasrc.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.topisenpai.lavasrc.applemusic.AppleMusicAudioPlaylist;
import com.github.topisenpai.lavasrc.applemusic.AppleMusicAudioTrack;
import com.github.topisenpai.lavasrc.deezer.DeezerAudioPlaylist;
import com.github.topisenpai.lavasrc.deezer.DeezerAudioTrack;
import com.github.topisenpai.lavasrc.spotify.SpotifyAudioPlaylist;
import com.github.topisenpai.lavasrc.spotify.SpotifyAudioTrack;
import com.github.topisenpai.lavasrc.yandexmusic.YandexMusicAudioPlaylist;
import com.github.topisenpai.lavasrc.yandexmusic.YandexMusicAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.arbjerg.lavalink.api.JsonPluginDataAppender;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class LavaSrcPluginDataAppender implements JsonPluginDataAppender {

	@Override
	public Map<String, JsonNode> addTrackPluginData(AudioTrack audioTrack) {
		var map = new HashMap<String, JsonNode>();

		if (audioTrack instanceof SpotifyAudioTrack) {
			var spotifyAudioTrack = (SpotifyAudioTrack) audioTrack;
			map.put("artworkURL", new TextNode(spotifyAudioTrack.getArtworkURL()));
			map.put("isrc", new TextNode(spotifyAudioTrack.getISRC()));

		} else if (audioTrack instanceof AppleMusicAudioTrack) {
			var appleMusicAudioTrack = (AppleMusicAudioTrack) audioTrack;
			map.put("artworkURL", new TextNode(appleMusicAudioTrack.getArtworkURL()));
			map.put("isrc", new TextNode(appleMusicAudioTrack.getISRC()));

		} else if (audioTrack instanceof DeezerAudioTrack) {
			var deezerAudioTrack = (DeezerAudioTrack) audioTrack;
			map.put("artworkURL", new TextNode(deezerAudioTrack.getArtworkURL()));
			map.put("isrc", new TextNode(deezerAudioTrack.getISRC()));
		} else if (audioTrack instanceof YandexMusicAudioTrack) {
			var yandexMusicAudioTrack = (YandexMusicAudioTrack) audioTrack;
			map.put("artworkURL", new TextNode(yandexMusicAudioTrack.getArtworkURL()));
		}

		return map;
	}

	@Override
	public Map<String, JsonNode> addPlaylistPluginData(AudioPlaylist playlist) {
		var map = new HashMap<String, JsonNode>();

		if (playlist instanceof SpotifyAudioPlaylist) {
			var spotifyPlaylist = (SpotifyAudioPlaylist) playlist;
			map.put("type", new TextNode(spotifyPlaylist.getType()));
			map.put("identifier", new TextNode(spotifyPlaylist.getIdentifier()));
			map.put("artworkURL", new TextNode(spotifyPlaylist.getArtworkURL()));
			map.put("author", new TextNode(spotifyPlaylist.getAuthor()));

		} else if (playlist instanceof AppleMusicAudioPlaylist) {
			var appleMusicPlaylist = (AppleMusicAudioPlaylist) playlist;
			map.put("type", new TextNode(appleMusicPlaylist.getType()));
			map.put("identifier", new TextNode(appleMusicPlaylist.getIdentifier()));
			map.put("artworkURL", new TextNode(appleMusicPlaylist.getArtworkURL()));
			map.put("author", new TextNode(appleMusicPlaylist.getAuthor()));

		} else if (playlist instanceof DeezerAudioPlaylist) {
			var deezerPlaylist = (DeezerAudioPlaylist) playlist;
			map.put("type", new TextNode(deezerPlaylist.getType()));
			map.put("identifier", new TextNode(deezerPlaylist.getIdentifier()));
			map.put("artworkURL", new TextNode(deezerPlaylist.getArtworkURL()));
			map.put("author", new TextNode(deezerPlaylist.getAuthor()));

		} else if (playlist instanceof YandexMusicAudioPlaylist) {
			var yandexMusicPlaylist = (YandexMusicAudioPlaylist) playlist;
			map.put("type", new TextNode(yandexMusicPlaylist.getType()));
			map.put("identifier", new TextNode(yandexMusicPlaylist.getIdentifier()));
			map.put("artworkURL", new TextNode(yandexMusicPlaylist.getArtworkURL()));
			map.put("author", new TextNode(yandexMusicPlaylist.getAuthor()));
		}

		return map;
	}

}
