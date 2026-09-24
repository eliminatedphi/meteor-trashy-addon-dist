// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;

public class SlotHighlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> combinable = sgGeneral.add(new BoolSetting.Builder()
        .name("Combinable slots")
        .description("Highlight combinable slots.")
        .defaultValue(false)
        .build());
    private AbstractContainerMenu containerMenu;
    private final ArrayList<Color> slotColors;
    private static final Color transparent = new Color(0, 0, 0, 0);
    private int update; // 1 = update, -1 = clear

    public SlotHighlight() {
        super(Categories.Render, "slot-highlight", "Highlight certain items in your inventory.");
        slotColors = new ArrayList<>();
        update = 0;
    }

    @Override
    public void onActivate() { containerMenu = null; }

    public Color getSlotColor(int index) {
        if (!this.isActive() || index >= slotColors.size())
            return transparent;
        return slotColors.get(index);
    }

    public void updateColors() {
        // only call this from the game ticking thread to prevent data races
        if (!isActive() || containerMenu == null)
            return;
        slotColors.clear();
        for (int i = 0; i < containerMenu.slots.size(); ++i)
            slotColors.add(new Color(transparent));
        updateMapsColors();
        if (!(containerMenu instanceof InventoryMenu) && combinable.get()) {
            updateCombinableSlotsColors();
        }
    }

    private void updateCombinableSlotsColors() {
        int firstInventorySlot = containerMenu.slots.size() - 36;
        if (firstInventorySlot <= 0)
            return;
        int colorIndex = 0;
        for (int i = 0; i < firstInventorySlot; ++ i) {
            if (containerMenu.getSlot(i).getItem().isEmpty())
                continue;
            for (int j = firstInventorySlot; j < containerMenu.slots.size(); ++ j) {
                if (containerMenu.getSlot(j).getItem().isEmpty())
                    continue;
                if (ItemStack.isSameItemSameComponents(containerMenu.getSlot(i).getItem(), containerMenu.getSlot(j).getItem()) ||
                    ItemStack.isSameItemSameComponents(containerMenu.getSlot(j).getItem(), containerMenu.getSlot(i).getItem())) {
                    Color c;
                    if (!slotColors.get(i).equals(transparent))
                        c = new Color(slotColors.get(i));
                    else if (!slotColors.get(j).equals(transparent))
                        c = new Color(slotColors.get(j));
                    else {
                        c = Color.fromHsv(colorIndex * 45, 0.6, 1.).a(160);
                        ++ colorIndex;
                        if (colorIndex > 7) colorIndex = 0;
                    }
                    slotColors.set(i, new Color(c));
                    slotColors.set(j, new Color(c));
                }
            }
        }
    }

    private void updateMapsColors() {
        for (int i = 0; i < containerMenu.slots.size(); ++i) {
            Function<ItemStack, Color> map_color = is -> {
                MapId m = is.get(DataComponents.MAP_ID);
                //info(m == null ? "(null)" : m.toString() + " vs " + (maphighlight.isPresent() ? maphighlight.get().getA() : " (null)") );
                if (m == null) return null;
                return MapmanIntegration.instance.getSlotHighlightForMap(m.id());
            };
            Function<ItemStack, Color> bundle_color = is -> {
                BundleContents bc = is.get(DataComponents.BUNDLE_CONTENTS);
                if (bc == null) return null;
                return bc.items().stream().map(ist -> map_color.apply(ist.create())).filter(Objects::nonNull).findFirst().orElse(null);
            };
            Function<ItemStack, Color> shulker_color = is -> {
                ItemContainerContents icc = is.get(DataComponents.CONTAINER);
                if (icc == null) return null;
                return icc.allItemsCopyStream().map(iis -> {
                    Color m = map_color.apply(iis);
                    if (m != null) return m;
                    Color b = bundle_color.apply(iis);
                    return b;
                }).filter(Objects::nonNull).findFirst().orElse(null);
            };
            ItemStack s = containerMenu.getSlot(i).getItem();
            Color c = map_color.apply(s);
            if (c == null) {
                c = bundle_color.apply(s);
                if (c == null) {
                    c = shulker_color.apply(s);
                }
            }
            if (c != null) {
                slotColors.set(i, c);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        switch (update) {
            case -1: slotColors.clear(); update = 0; return;
            case  0: return;
            case  1: updateColors(); update = 0; return;
            default: update = 0;
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        containerMenu = mc.player.containerMenu;
        if (containerMenu == null)
            return;
        update = 1;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof InventoryScreen) {
            containerMenu = mc.player.inventoryMenu;
            update = 1;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive e) {
        if (e.packet instanceof ClientboundContainerSetSlotPacket p) {
            if (containerMenu != null && p.getContainerId() == containerMenu.containerId) {
                update = 1;
            }
        } else if (e.packet instanceof ClientboundContainerClosePacket) {
            containerMenu = null;
            update = -1;
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send e) {
        if (e.packet instanceof ServerboundContainerClosePacket) {
            containerMenu = null;
            update = -1;
        }
    }
}
