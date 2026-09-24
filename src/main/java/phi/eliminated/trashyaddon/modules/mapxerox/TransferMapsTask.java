// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import it.unimi.dsi.fastutil.Pair;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.List;

public class TransferMapsTask extends XeroxTask {
    // (mapid, shulker slot)
    private final List<Pair<Integer, Integer>> transfers;
    private final Direction direction;
    // only used for FROM_INVENTORY transfers
    private final boolean half;
    private int index;
    private int step;
    private boolean complete;

    public TransferMapsTask(List<Pair<Integer, Integer>> transfers, Direction direction, boolean half) {
        this.transfers = transfers;
        this.direction = direction;
        this.half = half;
        index = 0;
        step = 0;
        complete = false;
        if (this.transfers.isEmpty()) {
            warning("TransferMapsTask: transfer list is empty.");
            complete = true;
        }
        checkSpaces();
    }

    private void checkSpaces() {
        if (direction == Direction.TO_INVENTORY) {
            int free = 0;
            for (int i = 0; i <= mc.player.getInventory().getContainerSize(); ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) ++ free;
            }
            if (free < transfers.size()) {
                error("TransferMapsTask: Not enough space in inventory. (%d required, %d found)", transfers.size(), free);
                MapXerox.instance.panic();
            }
        } else {
            ShulkerBoxScreen sbs = assertScreen(ShulkerBoxScreen.class);
            for (var transfer : transfers) {
                int slot = transfer.second();
                ItemStack stack = sbs.getMenu().getSlot(slot).getItem();
                if (!stack.isEmpty()) {
                    error("TransferMapsTask: Target slot is not empty. (%d)", slot);
                    MapXerox.instance.panic();
                }
            }
        }
    }

    private int tickDownward() {
        ShulkerBoxScreen sbs = assertScreen(ShulkerBoxScreen.class);
        if (index >= transfers.size()) return IMMEDIATE;

        int mapid = transfers.get(index).first();
        int slot = transfers.get(index).second();
        ItemStack stack = sbs.getMenu().getSlot(slot).getItem();
        MapId smapid = stack.get(DataComponents.MAP_ID);
        if (smapid == null || smapid.id() != mapid) {
            error("Unexpected transferred item. (Slot %d, expected map id %d)", slot, mapid);
            MapXerox.instance.panic();
        }

        shiftClickSlot(sbs.getMenu().containerId, slot);
        if (++index >= transfers.size()) {
            complete = true;
        }
        return DEFAULT;
    }

    private int tickUpward() {
        ShulkerBoxScreen sbs = assertScreen(ShulkerBoxScreen.class);
        if (index >= transfers.size()) return IMMEDIATE;

        MapId mapid = new MapId(transfers.get(index).first());
        int slot = transfers.get(index).second();

        if (++ step == 1) {
            FindItemResult map = InvUtils.find(s -> mapid.equals(s.get(DataComponents.MAP_ID)));
            if (map.found()) {
                if (half)
                    secondaryClickSlot(sbs.getMenu().containerId, SlotUtils.indexToId(map.slot()));
                else
                    clickSlot(sbs.getMenu().containerId, SlotUtils.indexToId(map.slot()));
            }
            else {
                error("TransferMapsTask: Map with id %d not found in inventory.", mapid.id());
                MapXerox.instance.panic();
            }
        } else {
            ItemStack stack = sbs.getMenu().getSlot(slot).getItem();
            if (!stack.isEmpty()) {
                error("TransferMapTask: Target slot is not empty. (Slot %d)", slot);
                MapXerox.instance.panic();
            }
            clickSlot(sbs.getMenu().containerId, slot);
            step = 0;
            if (++index >= transfers.size()) {
                complete = true;
            }
        }
        return DEFAULT;
    }

    @Override
    public int tick() {
        if (direction == Direction.TO_INVENTORY)
            return tickDownward();
        else
            return tickUpward();
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    public enum Direction {
        TO_INVENTORY,
        FROM_INVENTORY
    }
}
