// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

// Make a clone of a bundle inside a shulker, and place the resulting items into appropriate shulker boxes:
// 1. move the bundle into inventory
// 2. close the source shulker box
// 14. clone the bundle using CloneBundleTask
// 15. open the source shulker box
// 16. return the original bundle to its slot
// 17. close the source shulker box
// 18. open the destination shulker box
// 19. place the cloned bundle into the same slot
// 20. close the destination shulker box
// Assumes source shulker is open at the beginning
public class CloneBundleInShulkerTask extends XeroxTask {
    private final int bundleSlotInShulker;
    private final MapXerox.BlockInteractState srcShulker;
    private final MapXerox.BlockInteractState dstShulker;
    private final MapXerox.BlockInteractState anvil;
    private int freeInventorySlot; // raw #
    private int clonedBundleInvSlot; // raw #
    private CloneBundleTask bundleTask;
    private int step;
    private boolean complete;

    public CloneBundleInShulkerTask(int bundleSlotInShulker,
                                    MapXerox.BlockInteractState srcShulker,
                                    MapXerox.BlockInteractState dstShulker,
                                    MapXerox.BlockInteractState anvil) {
        this.bundleSlotInShulker = bundleSlotInShulker;
        this.srcShulker = srcShulker;
        this.dstShulker = dstShulker;
        this.anvil = anvil;
        bundleTask = null;
        clonedBundleInvSlot = -1;
        step = 0;
        complete = false;
    }

    @Override
    public int tick() {
        switch (step ++) {
            case 0: { // pick up bundle and find free inventory slot for it
                ShulkerBoxScreen sbs = assertScreen(ShulkerBoxScreen.class);
                ItemStack s = sbs.getMenu().getSlot(bundleSlotInShulker).getItem();
                BundleContents c = s.get(DataComponents.BUNDLE_CONTENTS);
                if (c == null || c.items().isEmpty()) {
                    error("CloneBundleInShulkerTask created on slot that does not contain a bundle, or the bundle is empty. (Slot %d)", bundleSlotInShulker);
                    MapXerox.instance.panic();
                }
                FindItemResult empty = InvUtils.findEmpty();
                if (!empty.found()) {
                    error("No free space in inventory.");
                    MapXerox.instance.panic();
                }
                freeInventorySlot = empty.slot();
                clickSlot(sbs.getMenu().containerId, bundleSlotInShulker);
                break;
            }
            case 1: { // put it in free slot
                ShulkerBoxScreen sbs = assertScreen(ShulkerBoxScreen.class);
                clickSlot(sbs.getMenu().containerId, SlotUtils.indexToId(freeInventorySlot));
                break;
            }
            case 2:
            case 10:
                MapXerox.instance.closeInventory();
                break;
            case 3: // open inventory gui
                MapXerox.instance.openInventory();
                break;
            case 4: // start CloneBundleTask
                bundleTask = new CloneBundleTask(SlotUtils.indexToId(freeInventorySlot), anvil);
                MapXerox.instance.newTask(bundleTask);
                return IMMEDIATE;
            case 5: // get cloned bundle slot and close inventory gui
                clonedBundleInvSlot = bundleTask.getDstBundleInvSlot();
                MapXerox.instance.closeInventory();
                break;
            case 6:
                Rotations.rotate(srcShulker.yaw(), srcShulker.pitch(), 0 ,true, null);
                // don't click blocks with empty map
                if (!Utils.switchToSafeItemForInteract())
                    warning("Unable to find safe item to interact block with.");
                break;
            case 7:
                BlockUtils.interact(srcShulker.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 8:
                if (mc.gui.screen() instanceof ShulkerBoxScreen sbs) {
                    clickSlot(sbs.getMenu().containerId, SlotUtils.indexToId(freeInventorySlot));
                } else {
                    -- step;
                    return IMMEDIATE;
                }
                break;
            case 9:
            case 14: {
                ShulkerBoxScreen sbs = assertScreen(ShulkerBoxScreen.class);
                clickSlot(sbs.getMenu().containerId, bundleSlotInShulker);
                break;
            }
            case 11:
                Rotations.rotate(dstShulker.yaw(), dstShulker.pitch(), 0 ,true, null);
                // don't click blocks with empty map
                if (!Utils.switchToSafeItemForInteract())
                    warning("Unable to find safe item to interact block with.");
                break;
            case 12:
                BlockUtils.interact(dstShulker.hit(), InteractionHand.MAIN_HAND, true);
                return UNTIL_CONTAINER_CONTENT_SET;
            case 13:
                if (mc.gui.screen() instanceof ShulkerBoxScreen sbs) {
                    clickSlot(sbs.getMenu().containerId, SlotUtils.indexToId(clonedBundleInvSlot));
                } else {
                    -- step;
                    return IMMEDIATE;
                }
                break;
            case 15:
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
}
