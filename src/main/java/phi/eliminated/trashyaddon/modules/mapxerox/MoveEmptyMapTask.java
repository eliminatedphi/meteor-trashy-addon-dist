// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Make sure slot 2 in the crafting inventory has at least mapsRequired empty maps.
// If mapsRequired is 0, clears that slot instead.
public class MoveEmptyMapTask extends XeroxTask {
    private final int mapsRequired;
    private int step;
    public MoveEmptyMapTask(int mapsRequired) {
        this.mapsRequired = mapsRequired;
        step = 0;
        assertScreen(InventoryScreen.class);
        ItemStack s = mc.player.inventoryMenu.getSlot(2).getItem();
        if (s.is(Items.MAP) && s.count() >= mapsRequired && mapsRequired != 0)
            step = 99;
        else {
            if (s.isEmpty()) step = 1;
        }
    }

    @Override
    public int tick() {
        assertScreen(InventoryScreen.class);
        switch (step ++) {
            case 0:
                shiftClickSlot(0, 2);
                break;
            case 1: {
                if (mapsRequired == 0) {
                    step = 99;
                    return IMMEDIATE;
                }
                FindItemResult map = Utils.findBiggestStack(st -> st.is(Items.MAP));
                if (map.found() && map.count() >= mapsRequired) {
                    clickSlot(0, SlotUtils.indexToId(map.slot()));
                } else {
                    error("You don't have enough empty maps.");
                    MapXerox.instance.panic();
                }
                break;
            }
            case 2:
                clickSlot(0, 2);
                break;
            default:
                return IMMEDIATE;
        }
        return DEFAULT;
    }

    @Override
    public boolean isComplete() {
        return step > 2;
    }
}
