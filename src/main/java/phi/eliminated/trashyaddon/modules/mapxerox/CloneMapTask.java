// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;

// Clone a single filled map in inventory, using empty maps currently in inventory.
// Assumes no maps of the same ID in other slots.
// All slot numbers are in crafting inventory numbering.
// Remaining empty maps may be returned to a different slot after crafting.
public class CloneMapTask extends XeroxTask {
    private final int filledMapSlot;
    private final boolean preserveFilledMapSlot;
    private final boolean keepEmptyMapsInCrafting;
    private int outputInvSlot; // raw inventory slot
    private MapId mapid;
    private int mapCount;
    private int step;
    private boolean dontClose;
    private boolean complete;
    public CloneMapTask(int filledMapSlot, boolean preserveFilledMapSlot, boolean keepEmptyMapsInCrafting) {
        this.filledMapSlot = filledMapSlot;
        this.preserveFilledMapSlot = preserveFilledMapSlot;
        this.keepEmptyMapsInCrafting = keepEmptyMapsInCrafting;
        step = 0;
        dontClose = false;
        complete = false;
        ItemStack stack = mc.player.inventoryMenu.getSlot(filledMapSlot).getItem();
        if (stack.count() > 32) {
            error("Trying to clone a stack that is too large. This will break things. (Slot %d)", filledMapSlot);
            MapXerox.instance.panic();
        }
        mapCount = stack.count();
        MapId id = stack.get(DataComponents.MAP_ID);
        if (id == null) {
            // invalid task or server messing with us
            error("Item in filled map slot has no mapId?? (Slot %d)", filledMapSlot);
            MapXerox.instance.panic();
        }
        else this.mapid = id;
    }

    @Override
    public int tick() {
        if (MapXerox.instance.verbose())
            info("ticking CloneMapTask %d @step %d", filledMapSlot, step);
        if (step >= 1 && step <= 8)
            assertScreen(InventoryScreen.class);
        switch (step ++) {
            case 0: // open inventory if needed
                // skip open & closing inventory if it's already open
                if (mc.gui.screen() instanceof InventoryScreen) {
                    dontClose = true;
                    ++ step;
                    // fall through to step 1
                } else {
                    // yes I know this is wholly unnecessary, but _look at it go!_
                    // also we do use SlotUtils.indexToId, which does kinda make it necessary ...
                    MapXerox.instance.openInventory();
                    break;
                }
            case 1: { // pick up filled map and check for empty maps
                clickSlot(0, filledMapSlot);
                break;
            }
            case 2: // put it in crafting gird
                clickSlot(0, 1);
                break;
            case 3: // put empty map in crafting grid
                MapXerox.instance.newTask(new MoveEmptyMapTask(mapCount));
                break;
            case 4: // check for output and craft
                if (mc.player.inventoryMenu.getSlot(0).getItem().isEmpty()) {
                    -- step;
                    return IMMEDIATE; // do it every tick
                }
                else
                    shiftClickSlot(0, 0);
                break;
            case 5: { // pick up crafting output
                FindItemResult find = InvUtils.find(s -> mapid.equals(s.get(DataComponents.MAP_ID)));
                if (find.found()) {
                    if (preserveFilledMapSlot)
                        clickSlot(0, SlotUtils.indexToId(find.slot()));
                    else {
                        outputInvSlot = find.slot();
                        step = 7;
                        return IMMEDIATE;
                    }
                } else {
                    // wait for item transfer?
                    -- step;
                    return IMMEDIATE;
                }
                break;
            }
            case 6: // put it back to original slot
                clickSlot(0, filledMapSlot);
                break;
            case 7: // return remaining empty maps to inventory if needed
                    // yes this is not necessary and inefficient but it causes me less headache
                if (preserveFilledMapSlot) {
                    FindItemResult find = InvUtils.find(s -> mapid.equals(s.get(DataComponents.MAP_ID)));
                    if (find.found()) {
                        outputInvSlot = find.slot();
                    } else {
                        error("What is happening?!");
                        MapXerox.instance.panic();
                    }
                }
                if (mc.player.inventoryMenu.getSlot(2).getItem().isEmpty()) {
                    if (!dontClose) MapXerox.instance.closeInventory();
                    complete = true;
                    break;
                }
                else {
                    if (!keepEmptyMapsInCrafting) {
                        shiftClickSlot(0, 2);
                        if (dontClose) complete = true;
                        break;
                    } // else fall through
                }
            default:
                if (!dontClose) MapXerox.instance.closeInventory();
                complete = true;
        }
        return DEFAULT;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    // Raw inventory slot
    public int getOutputSlot() {
        return outputInvSlot;
    }
}
