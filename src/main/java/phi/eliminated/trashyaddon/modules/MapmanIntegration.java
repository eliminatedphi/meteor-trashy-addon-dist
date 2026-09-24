// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import phi.eliminated.trashyaddon.modules.mapmansupport.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MapmanIntegration extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<String> mapmanSocketPath = sgGeneral.add(new StringSetting.Builder()
        .name("Socket Path")
        .description("Path to the socket used to contact Bibliotheca Arcana.")
        .defaultValue("/tmp/mapman.socket")
        .build());
    private final Setting<Boolean> forceMapIdTooltip = sgGeneral.add(new BoolSetting.Builder()
        .name("Always show map id tooltip")
        .description("Applies to all filled maps that has a map_id component.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> frameShowId = sgGeneral.add(new BoolSetting.Builder()
        .name("Show map id for unnamed maps in frames")
        .description("When an item frame has an unnamed map in it, show its map id in place of its name.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> verbose = sgGeneral.add(new BoolSetting.Builder()
        .name("Verbose Output")
        .description("Increased logging verbosity for troubleshooting.")
        .defaultValue(false)
        .build());

    private final Queue<MapmanClientboundPacket> pq = new LinkedList<>();
    private final Queue<MapFoundFramePacket> fq = new LinkedList<>(); // used to throttle found frame packet with data to 1 packet/tick
    private final HashMap<Integer, Integer> highlightSlotMaps = new HashMap<>();
    private final HashMap<Integer, Integer> highlightFrameMaps = new HashMap<>();
    private MapFoundContainerPacket fcp;
    private int fcpdelay;
    private Thread clientListener;
    private Path tempp;
    private Path socketp;
    private ServerSocketChannel ssc;

    public static MapmanIntegration instance;

    public MapmanIntegration() {
        super(Categories.World, "BA-integration", "Provides integration with the Bibliotheca Arcana map art library management utility.");
        instance = this;
        fcp = null;
    }

    public Color getSlotHighlightForMap(int id) {
        if (!highlightSlotMaps.containsKey(id)) return null;
        return new Color(highlightSlotMaps.get(id));
    }

    public void setSlotHighlightForMap(int id, Color c) {
        if (c == null) highlightSlotMaps.remove(id);
        else highlightSlotMaps.put(id, c.getPacked());
    }

    public void clearSlotHighlight() { highlightSlotMaps.clear(); }

    public Color getFrameHighlightForMap(int id) {
        if (!highlightFrameMaps.containsKey(id)) return null;
        return new Color(highlightFrameMaps.get(id));
    }

    public Color getItemFrameHighlight(Entity e) {
        if (e instanceof ItemFrame ife) {
            MapId id = ife.getItem().get(DataComponents.MAP_ID);
            if (id != null) {
                return getFrameHighlightForMap(id.id());
            }
        }
        return null;
    }

    public void setFrameHighlightForMap(int id, Color c) {
        if (c == null) highlightFrameMaps.remove(id);
        else highlightFrameMaps.put(id, c.getPacked());
    }

    public void clearFrameHighlight() { highlightFrameMaps.clear(); }

    public boolean itemFrameIdRender() { return isActive() && frameShowId.get(); }

    public void checkEntityData(Entity e) {
        if (!isActive()) return;
        if (e instanceof ItemFrame ife) {
            MapId id = ife.getItem().get(DataComponents.MAP_ID);
            if (id != null) {
                MapFoundFramePacket fp = new MapFoundFramePacket(id.id(), "", false, 0, null);
                messageServer(MapmanServerboundPacket.MAP_FOUND_FRAME, fp.encode());
                if (verbose.get())
                    info("send map found frame without data %d", fp.id());
            }
        }
    }

    public void focusMap(@NotNull MapId id) {
        messageServer(MapmanServerboundPacket.FOCUS_MAP, new FocusMapPacket(id.id()).encode());
    }

    private void messageServer(byte packetType, ByteBuffer buf) {
        if (!isActive()) return;
        SocketChannel sc = null;
        try {
            sc = SocketChannel.open(StandardProtocolFamily.UNIX);
            sc.connect(UnixDomainSocketAddress.of(mapmanSocketPath.get()));
            ByteBuffer b = ByteBuffer.allocate(buf.capacity() + 1).order(ByteOrder.nativeOrder());
            b.put(packetType);
            b.put(buf);
            b.flip();
            int wsz = 0;
            while (b.hasRemaining()) wsz += sc.write(b);
        } catch (IOException e) {
            error("Couldn't contact bibliotheca arcana. %s", e.getMessage());
        } finally {
            if (sc != null) {
                try {
                    sc.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void connectToMapman() {
        tempp = null;
        socketp = null;
        ssc = null;
        try {
            tempp = Files.createTempDirectory("mapmanclient");
            socketp = tempp.resolve("socket");
            UnixDomainSocketAddress addr = UnixDomainSocketAddress.of(socketp);
            ssc = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            ssc.configureBlocking(false);
            ssc.bind(addr);
            ServerSocketChannel essc = ssc;
            ByteBuffer buf = ByteBuffer.allocate(20000).order(ByteOrder.nativeOrder());
            clientListener = new Thread(() -> {
                for (;;) {
                    try {
                        SocketChannel sc = essc.accept();
                        if (sc != null) {
                            buf.clear();
                            int sz = sc.read(buf);
                            buf.flip();
                            if (sz > 0) {
                                switch (buf.get()) {
                                    case MapmanClientboundPacket.HIGHLIGHT_MAP: {
                                        HighlightMapPacket p = new HighlightMapPacket();
                                        p.decode(buf);
                                        if (verbose.get())
                                            info("highlight map %d %06x %s", p.flag, p.color, p.mapid.stream().map(i -> Integer.toString(i)).collect(Collectors.joining(", ")));
                                        pq.add(p);
                                        break;
                                    }
                                    case MapmanClientboundPacket.REQUEST_MAP_DATA: {
                                        RequestMapDataPacket p = new RequestMapDataPacket();
                                        p.decode(buf);
                                        if (verbose.get())
                                            info("request map data %d %d", p.flag, p.id);
                                        pq.add(p);
                                        break;
                                    }
                                }
                            }
                            sc.close();
                        }
                    } catch (IOException ignored) {}
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (Thread.currentThread().isInterrupted()) break;
                }
            });
            clientListener.start();
        } catch (IOException e) {
            error("Couldn't connect to bibliotheca arcana. Module disabled. (%s)", e.getMessage());
            disable();
        }
        messageServer(MapmanServerboundPacket.CLIENT_CONNECT, new ClientConnectPacket(socketp.toString()).encode());
    }

    private void disconnectFromMapman() {
        if (clientListener != null) {
            clientListener.interrupt();
            try {
                clientListener.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            clientListener = null;
        } else return;
        messageServer(MapmanServerboundPacket.CLIENT_CONNECT, new ClientConnectPacket("").encode());
        if (ssc != null)
            try { ssc.close(); } catch (IOException ignored) { }
        if (socketp != null)
            try { Files.deleteIfExists(socketp); } catch (IOException ignored) { }
        if (tempp != null)
            try { Files.deleteIfExists(tempp); } catch (IOException ignored) {}
    }

    @EventHandler
    private void appendTooltip(ItemStackTooltipEvent event) {
        if (!forceMapIdTooltip.get()) return;
        MapId id = event.itemStack().get(DataComponents.MAP_ID);
        if (id == null) return;
        Component c = Component.literal(String.format("map#%d", id.id())).withStyle(ChatFormatting.GRAY);
        boolean set = false;
        for (int i = 0 ; i < event.list().size(); ++i) {
            if (event.list().get(i).getString().startsWith("ID #")) {
                set = true;
                event.set(i, c);
            }
        }
        if (!set) event.appendStart(c);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        while (!pq.isEmpty()) {
            var p = pq.remove();
            if (p instanceof HighlightMapPacket hp) {
                boolean frame = (hp.flag & HighlightMapPacket.FRAME) != 0;
                boolean item = (hp.flag & HighlightMapPacket.ITEM) != 0;
                boolean remove = (hp.flag & HighlightMapPacket.REMOVE) != 0;
                boolean clear = (hp.flag & HighlightMapPacket.CLEAR) != 0;
                if (frame) {
                    if (clear) highlightFrameMaps.clear();
                    if (remove) hp.mapid.forEach(highlightFrameMaps::remove);
                    else hp.mapid.forEach(i -> highlightFrameMaps.put(i, hp.color));
                }
                if (item) {
                    if (clear) highlightSlotMaps.clear();
                    if (remove) hp.mapid.forEach(highlightSlotMaps::remove);
                    else hp.mapid.forEach(i -> highlightSlotMaps.put(i, hp.color));
                    Modules.get().get(SlotHighlight.class).updateColors();
                }
            } else if (p instanceof RequestMapDataPacket rp) {
                MapItemSavedData d = null;
                Optional<String> customName = Optional.empty();
                if (mc.level != null) {
                    d = MapItem.getSavedData(new MapId(rp.id), mc.level);
                    if (d != null || rp.flag == 1) {
                        MapId mapId = new MapId(rp.id);
                        for (Entity e : mc.level.getEntities().getAll())
                            if (e instanceof ItemFrame ife) {
                                MapId frameMapId = ife.getItem().get(DataComponents.MAP_ID);
                                if (rp.flag == 1) {
                                    MapItemSavedData fd = MapItem.getSavedData(frameMapId, mc.level);
                                    if (frameMapId != null && fd != null) {
                                        String cn = ife.getItem().getOrDefault(DataComponents.CUSTOM_NAME, Component.literal("")).getString();
                                        MapFoundFramePacket fp = new MapFoundFramePacket(frameMapId.id(), cn, fd.locked, fd.scale, fd.colors);
                                        fq.add(fp);
                                    }
                                } else if (mapId.equals(frameMapId)) {
                                    customName = Optional.of(ife.getItem().getOrDefault(DataComponents.CUSTOM_NAME, Component.literal("")).getString());
                                    break;
                                }
                            }
                    }
                }
                if (d != null && customName.isPresent()) {
                    MapFoundFramePacket fp = new MapFoundFramePacket(rp.id, customName.get(), d.locked, d.scale, d.colors);
                    messageServer(MapmanServerboundPacket.MAP_FOUND_FRAME, fp.encode());
                    if (verbose.get())
                        info("sent map found frame %d", fp.id());
                    break;
                } else if (rp.flag != 1) pq.add(rp);
            }
        }
        if (!fq.isEmpty()) {
            MapFoundFramePacket p = fq.remove();
            messageServer(MapmanServerboundPacket.MAP_FOUND_FRAME, p.encode());
            if (verbose.get())
                info("sent map found frame %d", p.id());
        }
        if (fcp != null) {
            if (++fcpdelay > 5) {
                messageServer(MapmanServerboundPacket.MAP_FOUND_CONTAINER, fcp.encode());
                fcp = null;
            }
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        // doesn't work, id is always null ...
        if (event.entity instanceof ItemFrame ife) {
            MapId id = ife.getItem().get(DataComponents.MAP_ID);
            if (id != null) {
                MapFoundFramePacket fp = new MapFoundFramePacket(id.id(), "", false, 0, null);
                messageServer(MapmanServerboundPacket.MAP_FOUND_FRAME, fp.encode());
                if (verbose.get())
                    info("send map found frame without data %d", fp.id());
            }
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        Vector<Integer> maps = new Vector<>();
        for (ItemStack s : event.packet.items()) {
            Function<BundleContents, List<Integer>> bundleMaps = bc ->
                bc.items().stream().<Integer>mapMulti((ist, con) -> {
                    MapId i = ist.get(DataComponents.MAP_ID);
                    if (i != null) con.accept(i.id());
                }).toList();
            Function<ItemContainerContents, List<Integer>> containerMaps = icc ->
                icc.allItemsCopyStream().<Integer>mapMulti((stk, con) -> {
                    MapId i = stk.get(DataComponents.MAP_ID);
                    if (i != null) con.accept(i.id()); else {
                        BundleContents c = stk.get(DataComponents.BUNDLE_CONTENTS);
                        if (c != null) {
                            List<Integer> l = bundleMaps.apply(c);
                            l.forEach(con);
                        }
                    }
                }).toList();
            MapId id = s.get(DataComponents.MAP_ID);
            if (id != null) maps.add(id.id());
            else {
                BundleContents c = s.get(DataComponents.BUNDLE_CONTENTS);
                if (c != null) maps.addAll(bundleMaps.apply(c));
                else {
                    ItemContainerContents icc = s.get(DataComponents.CONTAINER);
                    if (icc != null) maps.addAll(containerMaps.apply(icc));
                }
            }
        }
        if (!maps.isEmpty()) {
            // this need throttling too, otherwise certain inventory actions on servers with AC
            // cause severe client lag
            fcp = new MapFoundContainerPacket(maps);
            fcpdelay = 0;
        }
    }

    @Override
    public void onActivate() {
        connectToMapman();
        reset();
    }

    @Override
    public void onDeactivate() {
        disconnectFromMapman();
        reset();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        connectToMapman();
        reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        disconnectFromMapman();
        reset();
    }

    private void reset() {
        pq.clear();
        highlightSlotMaps.clear();
        highlightFrameMaps.clear();
    }
}
