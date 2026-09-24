// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

// Make a clone of a map shulker box by doing the following:
// 1. open the source shulker box
// 2. transfer max(free inv space, total # of slots with maps) maps from shulker box using TransferMapsTask, recording their slot position in the container (id -> slot)
// 3. close the source shulker box
// 4. clone all maps in inventory using CloneMapsTask
// 5. open the source shulker box
// 6. place half of each map into their original slot using TransferMapsTask
// 7. close the source shulker box
// 8. open the destination shulker box
// 9. place the rest of each map into their corresponding slot using TransferMapsTask
// 10. close the destination shulker box
// 11. repeat 1-10 until all unbundled maps are cloned
// 12. open the source shulker box, find bundle that's not been cloned
// 13. clone bundle using CloneBundleInShulkerTask
// 14. repeat 11-12 until all bundles are cloned
public class CloneShulkerTask extends XeroxTask {
    private final MapXerox.BlockInteractState srcShulker;
    private final MapXerox.BlockInteractState dstShulker;
    private final MapXerox.BlockInteractState anvil;
    private final ArrayList<SlotState> slotState;
    private final Vector<Pair<Integer, Integer>> transferList;
    private int phase;
    private int step;
    private boolean complete;

    public CloneShulkerTask(MapXerox.BlockInteractState srcShulker,
                    MapXerox.BlockInteractState dstShulker,
                    MapXerox.BlockInteractState anvil) {
        this.srcShulker = srcShulker;
        this.dstShulker = dstShulker;
        this.anvil = anvil;
        this.slotState = new ArrayList<>(Collections.nCopies(27, new SlotState(0, SlotItem.EMPTY, false)));
        this.transferList = new Vector<>();
        phase = 0;
        step = 0;
        complete = false;
    }

    public int tickPhase0() { // "planning" phase
        switch (step ++) {
            case 0:
                Rotations.rotate(dstShulker.yaw(), dstShulker.pitch(), 0 ,true, null);
                // don't click blocks with empty map
                if (!Utils.switchToSafeItemForInteract())
                    warning("Unable to find safe item to interact block with.");
                break;
            case 1:
                BlockUtils.interact(dstShulker.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 2:
                if (mc.gui.screen() instanceof ShulkerBoxScreen sbs) {
                    for (int i = 0; i < 27; ++i)
                        if (!sbs.getMenu().getSlot(i).getItem().isEmpty()) {
                            error("CloneShulkerTask: Destination shulker is not empty.");
                            MapXerox.instance.panic();
                        }
                } else {
                    -- step;
                    return IMMEDIATE;
                }
                break;
            case 3:
                MapXerox.instance.closeInventory();
                break;
            case 4:
                Rotations.rotate(srcShulker.yaw(), srcShulker.pitch(), 0 ,true, null);
                break;
            case 5:
                BlockUtils.interact(srcShulker.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 6:
                if (mc.gui.screen() instanceof ShulkerBoxScreen sbs) {
                    int mapCount = 0;
                    for (int i = 0; i < 27; ++i) {
                        ItemStack stack = sbs.getMenu().getSlot(i).getItem();
                        if (stack.getItem() instanceof MapItem) {
                            MapId mapid = stack.get(DataComponents.MAP_ID);
                            if (mapid == null) {
                                error("CloneShulkerTask: Shulker contains map that doesn't have a map_id. (Slot %d)", i);
                                MapXerox.instance.panic();
                            }
                            ++mapCount;
                            slotState.set(i, new SlotState(mapid.id(), SlotItem.MAP, false));
                        } else if (stack.getItem() instanceof BundleItem) {
                            BundleContents c = stack.get(DataComponents.BUNDLE_CONTENTS);
                            if (c == null || c.items().isEmpty()) {
                                error("CloneShulkerTask: Shulker contains empty bundles. (Slot %d)", i);
                                MapXerox.instance.panic();
                            }
                            var r = Utils.countMapsInBundle(c);
                            mapCount += r.first();
                            if (r.second()) {
                                error("CloneShulkerTask: Shulker contains at least one bundle that has something other than maps. (Slot %d)", i);
                                MapXerox.instance.panic();
                            }
                            slotState.set(i, new SlotState(0, SlotItem.BUNDLE, false));
                        } else {
                                if (stack.isEmpty()) {
                                    slotState.set(i, new SlotState(0, SlotItem.EMPTY, true));
                                } else {
                                    error("CloneShulkerTask: Shulker contains something other than maps and bundles. (Slot %d)", i);
                                    MapXerox.instance.panic();
                                }
                        }
                    }
                    info("Found %d map(s) to clone.", mapCount);
                    if (MapXerox.instance.checkEmptyMaps()) {
                        FindItemResult maps = InvUtils.find(Items.MAP);
                        if (!maps.found() || maps.count() < mapCount) {
                            error("You don't have enough empty maps.");
                            MapXerox.instance.panic();
                        }
                    }
                } else {
                    -- step;
                }
                phase = 1;
                step = 0;
                return IMMEDIATE;
        }
        return DEFAULT;
    }

    public int tickPhase1() { // map cloning phase
        switch (step ++) {
            case 0: // build transfer list and start transfer, source shulker is implicitly open at this point
                assertScreen(ShulkerBoxScreen.class);
                transferList.clear();
                for (int i = 0; i < 27; ++i) {
                    var s = slotState.get(i);
                    if (s.item == SlotItem.MAP && !s.done) {
                        transferList.add(new IntIntImmutablePair(s.mapId, i));
                        slotState.set(i, new SlotState(s.mapId(), s.item(), true));
                    }
                }
                if (transferList.isEmpty()) {
                    phase = 2;
                    step = 0;
                    return IMMEDIATE;
                }
                MapXerox.instance.newTask(new TransferMapsTask(transferList, TransferMapsTask.Direction.TO_INVENTORY, false));
                return IMMEDIATE;
            case 1:
            case 7: // done transfer into source shulker
            case 11: // done transfer into destination shulker
                MapXerox.instance.closeInventory();
                break;
            case 2:
                MapXerox.instance.openInventory();
                break;
            case 3: { // find slots for transferred maps, and start CloneMapsTask
                List<Integer> mapSlots = transferList.stream().map(t -> {
                    MapId m = new MapId(t.first());
                    FindItemResult r = InvUtils.find(s -> m.equals(s.get(DataComponents.MAP_ID)));
                    if (!r.found()) {
                        error("CloneShulkerTask: Map %d vanished after transfer?", t.first());
                        MapXerox.instance.panic();
                    }
                    return SlotUtils.indexToId(r.slot());
                }).toList();
                MapXerox.instance.newTask(new CloneMapsTask(mapSlots, false));
                return IMMEDIATE;
            }
            case 4:
            case 12: // reopen source for loop
                Rotations.rotate(srcShulker.yaw(), srcShulker.pitch(), 0 ,true, null);
                // don't click blocks with empty map
                if (!Utils.switchToSafeItemForInteract())
                    warning("Unable to find safe item to interact block with.");
                break;
            case 5:
            case 13:
                BlockUtils.interact(srcShulker.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 6:
                if (mc.gui.screen() instanceof ShulkerBoxScreen) {
                    MapXerox.instance.newTask(new TransferMapsTask(transferList, TransferMapsTask.Direction.FROM_INVENTORY, true));
                } else {
                    -- step;
                }
                return IMMEDIATE;
            case 8:
                Rotations.rotate(dstShulker.yaw(), dstShulker.pitch(), 0 ,true, null);
                // don't click blocks with empty map
                if (!Utils.switchToSafeItemForInteract())
                    warning("Unable to find safe item to interact block with.");
                break;
            case 9:
                BlockUtils.interact(dstShulker.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 10:
                if (mc.gui.screen() instanceof ShulkerBoxScreen) {
                    MapXerox.instance.newTask(new TransferMapsTask(transferList, TransferMapsTask.Direction.FROM_INVENTORY, false));
                } else {
                    -- step;
                }
                return IMMEDIATE;
            case 14:
                if (mc.gui.screen() instanceof ShulkerBoxScreen) {
                    step = 0;
                } else {
                    -- step;
                }
                return IMMEDIATE;
        }
        return DEFAULT;
    }

    public int tickPhase2() { // bundle cloning phase
        switch (step ++) {
            case 0: {
                int bundleSlot = -1;
                for (int i = 0; i < 27; ++i) {
                    var s = slotState.get(i);
                    if (s.item == SlotItem.BUNDLE && !s.done) {
                        bundleSlot = i;
                        slotState.set(i, new SlotState(s.mapId(), s.item(), true));
                        break;
                    }
                }
                if (bundleSlot == -1) {
                    phase = 3;
                    step = 0;
                } else {
                    MapXerox.instance.newTask(new CloneBundleInShulkerTask(bundleSlot, srcShulker, dstShulker, anvil));
                }
                return IMMEDIATE;
            }
            case 1:
                Rotations.rotate(srcShulker.yaw(), srcShulker.pitch(), 0 ,true, null);
                // don't click blocks with empty map
                if (!Utils.switchToSafeItemForInteract())
                    warning("Unable to find safe item to interact block with.");
                break;
            case 2:
                BlockUtils.interact(srcShulker.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 3:
                if (mc.gui.screen() instanceof ShulkerBoxScreen) {
                    step = 0;
                } else {
                    -- step;
                }
                return IMMEDIATE;
        }
        return DEFAULT;
    }

    @Override
    public int tick() {
        switch (phase) {
            case 0: return tickPhase0();
            case 1: return tickPhase1();
            case 2: return tickPhase2();
            case 3:
                MapXerox.instance.closeInventory();
                complete = true;
                return IMMEDIATE;
        }
        return DEFAULT;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    private enum SlotItem {
        EMPTY,
        MAP,
        BUNDLE
    }

    private record SlotState(int mapId, SlotItem item, boolean done) {}
}
