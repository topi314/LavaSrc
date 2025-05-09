package com.github.topi314.lavasrc.plugin;

import com.github.topi314.lavasrc.extended.ExtendedAudioPlaylist;
import com.github.topi314.lavasrc.extended.ExtendedAudioTrack;
import com.github.topi314.lavasrc.source.spotify.SpotifyAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class LavaSrcAudioPluginInfoModifier implements AudioPluginInfoModifier {

	@Override
	public JsonObject modifyAudioPlaylistPluginInfo(@NotNull AudioPlaylist playlist) {
		var json = new HashMap<String, JsonElement>();

		if (playlist instanceof ExtendedAudioPlaylist extendedPlaylist) {
			json.put("type", JsonElementKt.JsonPrimitive(extendedPlaylist.getType().name));
			json.put("url", JsonElementKt.JsonPrimitive(extendedPlaylist.getUrl()));
			json.put("artworkUrl", JsonElementKt.JsonPrimitive(extendedPlaylist.getArtworkURL()));
			json.put("author", JsonElementKt.JsonPrimitive(extendedPlaylist.getAuthor()));
			json.put("totalTracks", JsonElementKt.JsonPrimitive(extendedPlaylist.getTotalTracks()));
		}

		return new JsonObject(json);
	}

	@Nullable
	@Override
	public JsonObject modifyAudioTrackPluginInfo(@NotNull AudioTrack track) {
		var json = new HashMap<String, JsonElement>();
		if (track instanceof ExtendedAudioTrack extendedTrack) {
			var info = extendedTrack.getExtendedInfo();

			json.put("albumName", JsonElementKt.JsonPrimitive(info.albumName));
			json.put("albumUrl", JsonElementKt.JsonPrimitive(info.albumUrl));
			json.put("artistUrl", JsonElementKt.JsonPrimitive(info.artistUrl));
			json.put("artistArtworkUrl", JsonElementKt.JsonPrimitive(info.artistArtworkUrl));
			json.put("isPreview", JsonElementKt.JsonPrimitive(info.isPreview));

			if (track instanceof SpotifyAudioTrack spotifyTrack) {
				json.put("isLocal", JsonElementKt.JsonPrimitive(spotifyTrack.isLocal()));
			}
		}
		return new JsonObject(json);
	}
}
