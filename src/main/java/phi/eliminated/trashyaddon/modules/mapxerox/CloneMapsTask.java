// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.ArrayList;
import java.util.List;

// clone multiple filled map in inventory, using empty maps currently in inventory
// Assumes no maps of the same ID in multiple slots.
// All slot numbers are in crafting inventory numbering.
public class CloneMapsTask extends XeroxTask {
    private final List<Integer> mapSlots;
    private final boolean preserveFilledMapSlot;
    private int step;
    private int index;
    private boolean complete;
    public CloneMapsTask(List<Integer> mapSlots, boolean preserveFilledMapSlot) {
        this.mapSlots = mapSlots;
        this.preserveFilledMapSlot = preserveFilledMapSlot;
        if (MapXerox.instance.verbose()) {
            StringBuilder sb = new StringBuilder();
            mapSlots.forEach(i -> { sb.append(i); sb.append(' '); });
            info("CloneMapsTask (" + mapSlots.size() + ") " + sb.toString());
        }
        complete = false;
        index = 0;
        step = 0;
    }

    @Override
    public int tick() {
        if (MapXerox.instance.verbose()) {
            info("ticking CloneMapsTask @step %d index %d", step, index);
        }
        switch (step) {
            case 0:
                MapXerox.instance.openInventory();
                ++ step;
                break;
            case 1:
                assertScreen(InventoryScreen.class);
                if (index < mapSlots.size()) {
                    MapXerox.instance.newTask(new CloneMapTask(mapSlots.get(index), preserveFilledMapSlot, index != mapSlots.size() - 1));
                    ++ index;
                    return IMMEDIATE;
                } else {
                    ++ step;
                }
                break;
            case 2:
                MapXerox.instance.closeInventory();
                complete = true;
                break;
        }
        return DEFAULT;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }
}
