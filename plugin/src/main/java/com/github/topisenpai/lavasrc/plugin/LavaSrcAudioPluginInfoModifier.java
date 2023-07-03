package com.github.topisenpai.lavasrc.plugin;

import com.github.topisenpai.lavasrc.ExtendedAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LavaSrcAudioPluginInfoModifier implements AudioPluginInfoModifier {

    @Override
    public JsonObject modifyAudioPlaylistPluginInfo(@NotNull AudioPlaylist playlist) {
        if (playlist instanceof ExtendedAudioPlaylist extendedPlaylist) {
            return new JsonObject(Map.of(
            "type", JsonElementKt.JsonPrimitive(extendedPlaylist.getType()),
            "url", JsonElementKt.JsonPrimitive(extendedPlaylist.getUrl()),
            "artworkUrl", JsonElementKt.JsonPrimitive(extendedPlaylist.getArtworkURL()),
            "author", JsonElementKt.JsonPrimitive(extendedPlaylist.getAuthor())
            ));
        }
        return null;
    }
}
