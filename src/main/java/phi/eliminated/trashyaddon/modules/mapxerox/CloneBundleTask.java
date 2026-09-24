// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.HashSet;
import java.util.stream.Collectors;

// Make a clone of a map bundle by doing the following:
// 1. crafting a new bundle, optionally name it on an anvil (dye your bundles yourself, you lazy bum)
// 2. from the source bundle, take out the last item (n.b. assumption below)
// 3. clone it using CloneMapTask
// 4. put half into original bundle
// 5. put the rest into new bundle
// 6. repeat 2-5 until all maps are copied
// Assumes server does not interfere with "illegal" bundle item selection (> 11 or > 12)
// Also assumes bundle contains ONLY filled maps
public class CloneBundleTask extends XeroxTask {
    private final int srcBundleSlot;
    private final MapXerox.BlockInteractState anvil;
    private int dstBundleSlot;
    private int dstBundleInvSlot;
    private String srcBundleName;
    private HashSet<Integer> mapIds;
    private CloneMapTask cloneMapTask;
    private int step;
    private int substep;
    private int renametries;
    private int currentMapId;
    private int tempSlot;
    private boolean complete;
    private boolean dontClose;

    // srcBundleSlot is in crafting inventory numbering
    public CloneBundleTask(int srcBundleSlot, MapXerox.BlockInteractState anvil) {
        this.srcBundleSlot = srcBundleSlot;
        this.anvil = anvil;
        dstBundleSlot = -1;
        dstBundleInvSlot = -1;
        tempSlot = -1;
        step = 0;
        substep = 0;
        complete = false;
        dontClose = false;
        ItemStack bundle = mc.player.inventoryMenu.getSlot(srcBundleSlot).getItem();
        if (bundle.getItem() instanceof BundleItem) {
            Component name = bundle.get(DataComponents.CUSTOM_NAME);
            srcBundleName = name != null ? name.getString() : "";
            BundleContents c = bundle.get(DataComponents.BUNDLE_CONTENTS);
            if (c != null)
                mapIds = c.items().stream().<Integer>mapMulti((is, con) -> {
                    MapId id = is.get(DataComponents.MAP_ID);
                    if (id != null)
                        con.accept(id.id());
                }).collect(Collectors.toCollection(HashSet::new));
            else mapIds = new HashSet<>();
        } else {
            error("CloneBundleTask created on item that's not a Bundle? (Slot %d)", srcBundleSlot);
            complete = true;
        }
    }

    private void selectSrcBundleSlot(int slot) {
        ItemStack bundle = mc.player.inventoryMenu.getSlot(srcBundleSlot).getItem();
        BundleItem.toggleSelectedItem(bundle, slot);
        mc.getConnection().send(new ServerboundSelectBundleItemPacket(srcBundleSlot, slot));
    }

    private int tickCloning() {
        assertScreen(InventoryScreen.class);
        switch (substep ++) {
            case 0: // select last item in source bundle
                ItemStack bundle = mc.player.inventoryMenu.getSlot(srcBundleSlot).getItem();
                BundleContents c = bundle.get(DataComponents.BUNDLE_CONTENTS);
                if (c != null && !c.items().isEmpty()) {
                    MapId mapid = c.items().getLast().get(DataComponents.MAP_ID);
                    if (mapid != null)
                        currentMapId = mapid.id();
                    else {
                        error("Last item in bundle has no map_id.");
                        MapXerox.instance.panic();
                    }
                    selectSrcBundleSlot(c.items().size() - 1);
                } else {
                    error("Source bundle has no content? (Slot %d)", srcBundleSlot);
                    MapXerox.instance.panic();
                }
                break;
            case 1: // take item out of bundle
                secondaryClickSlot(0, srcBundleSlot);
                break;
            case 2: // put into temporary slot, and clone it
                clickSlot(0, tempSlot);
                cloneMapTask = new CloneMapTask(tempSlot, false, dontClose || mapIds.size() > 1);
                MapXerox.instance.newTask(cloneMapTask);
                break;
            case 3: // take half in tempSlot
                tempSlot = SlotUtils.indexToId(cloneMapTask.getOutputSlot());
                secondaryClickSlot(0, tempSlot);
                ItemStack s = mc.player.inventoryMenu.getSlot(tempSlot).getItem();
                MapId mapid = s.get(DataComponents.MAP_ID);
                if (mapid != null) {
                    if (currentMapId != mapid.id()) {
                        error("Map taken out of the bundle is not the last map. Is the server hard capping bundle scrolling?");
                        MapXerox.instance.panic();
                    }
                } else {
                    error("Cloned map has no id.");
                    MapXerox.instance.panic();
                }
                break;
            case 4: // put into source bundle
                clickSlot(0, srcBundleSlot);
                break;
            case 5: // take the rest in tempSlot
                clickSlot(0, tempSlot);
                break;
            case 6: // put into destination bundle
                clickSlot(0, dstBundleSlot);
                break;
            case 7: // verify and do the next map
                if (mapIds.contains(currentMapId)) {
                    mapIds.remove(currentMapId);
                } else {
                    warning("Bundle item inconsistency detected.");
                }
                if (mapIds.isEmpty())
                    substep = 999;
                else substep = 0;
                return IMMEDIATE;
        }
        return DEFAULT;
    }

    @Override
    public int tick() {
        switch (step ++) {
            case 0: // open inventory if needed
                // skip open & closing inventory if it's already open
                if (mc.gui.screen() instanceof InventoryScreen) {
                    dontClose = true;
                    ++ step;
                    // fall through to step 1
                } else {
                    MapXerox.instance.openInventory();
                    break;
                }
            case 1: { // find string in inventory, pick it up
                assertScreen(InventoryScreen.class);
                FindItemResult string = InvUtils.find(Items.STRING);
                if (string.found()) {
                    clickSlot(0, SlotUtils.indexToId(string.slot()));
                } else {
                    error("Couldn't craft new bundle. You don't have any string.");
                    MapXerox.instance.panic();
                }
                break;
            }
            case 2: // put it in crafting grid
                assertScreen(InventoryScreen.class);
                clickSlot(0, 1);
                break;
            case 3: { // find leather in inventory, pick it up
                assertScreen(InventoryScreen.class);
                FindItemResult leather = InvUtils.find(Items.LEATHER);
                if (leather.found()) {
                    clickSlot(0, SlotUtils.indexToId(leather.slot()));
                } else {
                    error("Couldn't craft new bundle. You don't have any leather.");
                    MapXerox.instance.panic();
                }
                break;
            }
            case 4: // put it in crafting grid
                assertScreen(InventoryScreen.class);
                clickSlot(0, 3);
                break;
            case 5: // check for output and craft
                assertScreen(InventoryScreen.class);
                if (mc.player.inventoryMenu.getSlot(0).getItem().isEmpty()) {
                    -- step;
                    return IMMEDIATE; // do it every tick
                }
                else {
                    FindItemResult empty = InvUtils.findEmpty();
                    if (empty.found()) {
                        dstBundleInvSlot = empty.slot();
                        dstBundleSlot = SlotUtils.indexToId(dstBundleInvSlot);
                        clickSlot(0, 0);
                    } else {
                        error("Couldn't craft new bundle. No free space in inventory.");
                        MapXerox.instance.panic();
                    }
                }
                break;
            case 6: // put new bundle in free slot
                assertScreen(InventoryScreen.class);
                clickSlot(0, dstBundleSlot);
            case 7: // clear the crafting grid
                assertScreen(InventoryScreen.class);
                if (!mc.player.inventoryMenu.getSlot(1).getItem().isEmpty())
                    shiftClickSlot(0, 1);
                else return IMMEDIATE;
                break;
            case 8: // clear the crafting grid
                assertScreen(InventoryScreen.class);
                if (!mc.player.inventoryMenu.getSlot(3).getItem().isEmpty())
                    shiftClickSlot(0, 3);
                else return IMMEDIATE;
                break;
            case 9: // if bundle needs renaming, close inventory
                if (srcBundleName != null && !srcBundleName.isEmpty() && anvil != null) {
                    MapXerox.instance.closeInventory();
                } else {
                    step = 18;
                }
                break;
            case 10:
                assert (anvil != null);
                Rotations.rotate(anvil.yaw(), anvil.pitch(), 0, true, null); //probably useless
                // don't click blocks with empty map
                if (!Utils.switchToSafeItemForInteract())
                    warning("Unable to find safe item to interact block with.");
                break;
            case 11:
                assert(anvil != null);
                BlockUtils.interact(anvil.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 12:
                if (mc.gui.screen() instanceof AnvilScreen asc) {
                    shiftClickSlot(asc.getMenu().containerId, SlotUtils.indexToId(dstBundleInvSlot));
                    renametries = 3;
                    break;
                } else {
                    -- step;
                    return IMMEDIATE;
                }
            case 13: {
                AnvilScreen asc = assertScreen(AnvilScreen.class);
                if (asc.name.getValue().isEmpty() || !asc.name.isActive()) {
                    -- step;
                    return IMMEDIATE;
                }
                asc.name.setValue(srcBundleName);
                return UNTIL_CONTAINER_DATA_SET;
            }
            case 14: {
                AnvilScreen asc = assertScreen(AnvilScreen.class);
                if (asc.getMenu().getSlot(2).mayPickup(mc.player)) {
                    // may break the anvil and close the gui, so use shift click
                    shiftClickSlot(asc.getMenu().containerId, asc.getMenu().getResultSlot());
                    // we need to find the slot again
                    dstBundleInvSlot = -1;
                    ++ step; // skip 15
                } else {
                    if (-- renametries < 0) {
                        clickSlot(asc.getMenu().containerId, 0);
                        warning("Couldn't rename bundle.");
                    } else { step -= 2; }
                }
                break;
            }
            case 15: { // rename failed
                AnvilScreen asc = assertScreen(AnvilScreen.class);
                clickSlot(asc.getMenu().containerId, SlotUtils.indexToId(dstBundleInvSlot));
                break;
            }
            case 16: // if the anvil breaks and inventory is closed by server, this shouldn't cause any issues
                     // key word "should"
                MapXerox.instance.closeInventory();
                break;
            case 17:
                MapXerox.instance.openInventory();
                break;
            case 18:
                if (dstBundleInvSlot == -1) { // rename succeeded, and we shift-clicked the result into some slot
                    FindItemResult dstBundle = InvUtils.find(s -> {
                        Component cn = s.get(DataComponents.CUSTOM_NAME);
                        BundleContents c = s.get(DataComponents.BUNDLE_CONTENTS);
                        return s.getItem() instanceof BundleItem
                            && cn != null && srcBundleName.equals(cn.getString())
                            && (c == null || c.items().isEmpty());
                    });
                    if (dstBundle.found()) {
                        dstBundleInvSlot = dstBundle.slot();
                        dstBundleSlot = SlotUtils.indexToId(dstBundleInvSlot);
                    }
                    else {
                        error("Renamed bundle disappeared from inventory?");
                        MapXerox.instance.panic();
                    }
                }
                if (tempSlot == -1) {
                    FindItemResult empty = InvUtils.findEmpty();
                    if (empty.found()) {
                        tempSlot = SlotUtils.indexToId(empty.slot());
                    } // else panic
                }
                int r = tickCloning();
                if (substep != 999)
                    step --;
                return r;
            case 19:
                if (!dontClose) MapXerox.instance.closeInventory();
                complete = true;
                return IMMEDIATE;
        }
        return DEFAULT;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }
    // result is a raw inventory slot # (for use with SlotUtils)
    public int getDstBundleInvSlot() {
        return dstBundleInvSlot;
    }
}
