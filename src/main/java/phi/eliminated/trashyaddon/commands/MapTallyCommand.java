// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.orbit.listeners.IListener;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

//TODO: bundled maps support
public class MapTallyCommand extends Command {
    IListener invListener;
    HashSet<Integer> ids = new HashSet<>();
    private final Minecraft mc = Minecraft.getInstance();
    public MapTallyCommand() {
        super("mt", "Tally maps inside containers opened after execution of this command.");
        invListener = null;
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("stop").executes(context -> {
            if (invListener != null) {
                info(ids.size() + " distinct map(s) total found.");
                try {
                    ByteBuffer buf = ByteBuffer.allocate(4 * ids.size());
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    for (int id : ids)
                        buf.putInt(id);
                    File dirpath = new File(MeteorClient.FOLDER, "mapman");
                    if (!dirpath.isDirectory())
                        dirpath.mkdir();
                    File dumppath = new File(dirpath, "mt-" + Util.getFilenameFormattedDateTime() + ".gz");
                    FileOutputStream fo = new FileOutputStream(dumppath);
                    GZIPOutputStream output = new GZIPOutputStream(fo);
                    output.write(buf.array());
                    output.close();
                    fo.close();
                    info("Tally saved to " + dumppath.toString());
                }
                catch (Exception e) {
                    error("Cannot write output:" + e.getMessage());
                    return 0;
                }
                ids.clear();
                MeteorClient.EVENT_BUS.unsubscribe(invListener);
                invListener = null;
            } else error("Map tally not running.");
            return SINGLE_SUCCESS;
        }));
        builder.executes(context -> {
            if (invListener == null) {
                ids.clear();
                invListener = new IListener() {
                    @Override
                    public void call(Object target) {
                        InventoryEvent e = (InventoryEvent)target;
                        List<ItemStack> stacks = e.packet.items();
                        if (stacks.size() == 46 || stacks.size() <= 36) {
                            // player inventory / 3x3 crafting table or invalid
                            return;
                        }
                        int nmaps = 0;
                        for (ItemStack s : stacks.subList(0, stacks.size() - 36)) {
                            if (s.getItem() instanceof MapItem) {
                                MapId idc = s.get(DataComponents.MAP_ID);
                                if (idc == null)
                                    continue;
                                ids.add(idc.id());
                                ++nmaps;
                            }
                        }
                        info("Found " + nmaps + " map(s) in that container.");
                    }
                    @Override public Class<?> getTarget() { return InventoryEvent.class; }
                    @Override public int getPriority() { return EventPriority.MEDIUM; }
                    @Override public boolean isStatic() { return false; }
                };
                MeteorClient.EVENT_BUS.subscribe(invListener);
                info("Open the containers that contain maps you wish to tally...");
            }
            else error("Already tallying!");
            return SINGLE_SUCCESS;
        });
    }
}
