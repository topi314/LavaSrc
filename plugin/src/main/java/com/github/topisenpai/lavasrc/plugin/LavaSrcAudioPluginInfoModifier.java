package com.github.topisenpai.lavasrc.plugin;

import com.github.topisenpai.lavasrc.ExtendedAudioPlaylist;
import com.github.topisenpai.lavasrc.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier;
import dev.arbjerg.lavalink.protocol.v4.Mapper;
import dev.arbjerg.lavalink.protocol.v4.Track;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonObject;
import lavalink.server.util.UtilKt;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LavaSrcAudioPluginInfoModifier implements AudioPluginInfoModifier {

    private final AudioPlayerManager playerManager;
    private final List<AudioPluginInfoModifier> pluginInfoModifiers;

    public LavaSrcAudioPluginInfoModifier(AudioPlayerManager playerManager, List<AudioPluginInfoModifier> pluginInfoModifiers) {
        this.playerManager = playerManager;
        this.pluginInfoModifiers = pluginInfoModifiers;
    }

    public JsonObject modifyAudioTrackPluginInfo(@NotNull AudioTrack track) {
        if (track instanceof MirroringAudioTrack) {
            var mirroringTrack = (MirroringAudioTrack) track;
            var delegate = mirroringTrack.getDelegate();
            if (delegate == null) {
                return null;
            }

            return new JsonObject(Map.of(
                "resolvedTrack", Mapper.getJson().encodeToJsonElement(Track.Companion.serializer(), UtilKt.toTrack(delegate, playerManager, pluginInfoModifiers))
            ));
        }
        return new JsonObject(Map.of(
                "test", JsonElementKt.JsonPrimitive("lol")
        ));
    }

    @Override
    public JsonObject modifyAudioPlaylistPluginInfo(@NotNull AudioPlaylist playlist) {
        if (playlist instanceof ExtendedAudioPlaylist) {
            var extendedPlaylist = (ExtendedAudioPlaylist) playlist;

            return new JsonObject(Map.of(
                 "type", JsonElementKt.JsonPrimitive(extendedPlaylist.getType()),
                 "identifier", JsonElementKt.JsonPrimitive(extendedPlaylist.getIdentifier()),
                 "artworkUrl", JsonElementKt.JsonPrimitive(extendedPlaylist.getArtworkURL()),
                 "author", JsonElementKt.JsonPrimitive(extendedPlaylist.getAuthor())
            ));
        }
        return null;
    }

}
