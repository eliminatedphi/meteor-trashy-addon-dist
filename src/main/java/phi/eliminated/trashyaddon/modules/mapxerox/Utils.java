// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntBooleanImmutablePair;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

import java.util.function.Predicate;

public class Utils {
    static Pair<Integer, Boolean> countMapsInBundle(BundleContents c) {
        boolean containsNonMap = c.items().stream().map(s -> s.get(DataComponents.MAP_ID) == null).reduce(false, Boolean::logicalOr);
        int mapCount = c.items().stream().<Integer>mapMulti((s, con) -> {
            if (s.get(DataComponents.MAP_ID) != null)
                con.accept(s.count());
        }).reduce(0, Integer::sum);
        return new IntBooleanImmutablePair(mapCount, containsNonMap);
    }
    static boolean switchToSafeItemForInteract() {
        if (InvUtils.testInMainHand(s -> s.is(Items.MAP) || s.getItem() instanceof BundleItem)) {
            FindItemResult interactItem = InvUtils.findInHotbar(s -> !(s.is(Items.MAP) || s.getItem() instanceof BundleItem));
            if (interactItem.found()) {
                InvUtils.swap(interactItem.slot(), false);
                return true;
            }
            return false;
        } else {
            return true;
        }
    }
    static FindItemResult findBiggestStack(Predicate<ItemStack> p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return new FindItemResult(0, 0);
        } else {
            int slot = -1;
            int maxCount = 0;
            for (int i = 0; i <= mc.player.getInventory().getContainerSize(); ++i) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (p.test(s) && s.count() > maxCount) {
                    slot = i;
                    maxCount = s.count();
                    if (maxCount == s.getMaxStackSize())
                        break;
                }
            }
            return new FindItemResult(slot, maxCount);
        }
    }
}
