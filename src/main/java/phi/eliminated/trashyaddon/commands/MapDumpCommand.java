// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.util.Util;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteOrder;
import java.util.zip.GZIPOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

public class MapDumpCommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    public MapDumpCommand() {
        super("md", "Dump all maps in view distance.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            int nmaps = 0;
            Vector<ByteBuffer> bufs = new Vector<>();
            for (Entity e : mc.level.getEntities().getAll()) {
                if (e.getType() != EntityTypes.ITEM_FRAME && e.getType() != EntityTypes.GLOW_ITEM_FRAME)
                    continue;
                ItemFrame ife = (ItemFrame) e;
                MapId idc = ife.getFramedMapId(ife.getItem());
                if (idc == null)
                    continue;
                int id = idc.id();
                MapItemSavedData mapState = MapItem.getSavedData(idc, mc.level);
                if (mapState == null)
                    continue;
                String name = ife.getItem().getOrDefault(DataComponents.CUSTOM_NAME, Component.literal("")).getString();
                byte[] u8name = name.getBytes(StandardCharsets.UTF_8);
                if (u8name.length > 0xfffffff) {
                    // if somehow this happens ... something has gone really wrong
                    continue;
                }
                int scale = mapState.scale >= 0 && mapState.scale < 5 ? mapState.scale : 0;
                int bsz = 4 + 4 + u8name.length + 16384;
                ByteBuffer buf = ByteBuffer.allocate(bsz);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(id);
                buf.putInt(u8name.length  | (mapState.locked ? 0x80000000 : 0) | (scale << 28));
                buf.put(u8name);
                buf.put(mapState.colors);
                bufs.add(buf);
                ++nmaps;
            }
            info("Maps found: " + nmaps);
            try {
                File dirpath = new File(MeteorClient.FOLDER, "mapman");
                if (!dirpath.isDirectory())
                    dirpath.mkdir();
                File dumppath = new File(dirpath, "md-" + Util.getFilenameFormattedDateTime() + ".gz");
                FileOutputStream fo = new FileOutputStream(dumppath);
                GZIPOutputStream output = new GZIPOutputStream(fo);
                for (ByteBuffer buf : bufs)
                    output.write(buf.array());
                output.close();
                fo.close();
                info("Maps dumped to " + dumppath.toString());
            }
            catch (Exception e) {
                error("Cannot write output:" + e.getMessage());
                return 0;
            }
            return SINGLE_SUCCESS;
        });
    }
}
