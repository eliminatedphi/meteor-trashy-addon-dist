// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Stack;
import java.util.Vector;

// All rotation tuples used in this package are (pitch, yaw) / (xRot, yRot)
// Assumes Map IDs are stable, that is they won't change while being moved across containers.
// (I don't know if that's true on certain servers, including specifically 2b2t. But I don't play
// on that server any more, so I don't care.)
// Also, kinda assumes player doesn't move in the process...
// Finally, if the process is interrupted somehow, there's no automated way of resuming it...
/*
MapXerox works the best when you:
 - have some free space in inventory (>= 3 slots)
 - have lots of empty map supply (27 stacks if cloning shulkerful of full bundles...)
 - have strings and leather in inventory (for crafting bundles)
 - inventory is largely free of unrelated items (maps and bundles not part of your cloning task, etc)
 - have access to anvils for renaming the bundles (stacked anvils recommended, renaming is optional)
 - have at least one unrelated item in the hotbar for interacting with blocks
 - have a map storage scheme that is sane (e.g. don't have huge stack of maps of the same id)
 - are in a safe location!!!
 - note on interaction rate: Map Xerox seems to work the best when the interaction rate matches your
   connection latency (ping). Each tick is 1/20s, so for a ~100ms ping, setting interaction rate to
   2 ticks should work decently.
*/
public class MapXerox extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> interactionRate = sgGeneral.add(new IntSetting.Builder()
        .name("Interaction Rate")
        .description("Number of ticks between interactions.")
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Boolean> skipMapCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("Skip Empty Maps Check")
        .description("Don't check if you have enough empty maps before cloning starts.")
        .defaultValue(false)
        .build());
    private final Setting<Boolean> verbose = sgGeneral.add(new BoolSetting.Builder()
        .name("Verbose Output")
        .description("Increased logging verbosity for troubleshooting.")
        .defaultValue(false)
        .build());
    private final Stack<XeroxTask> tasks = new Stack<>();
    private int ticks = 0;
    private int tickPacket = 0;
    private WaitMode waitMode;
    private BlockInteractState srcShulker;
    private BlockInteractState dstShulker;
    private BlockInteractState anvil;

    public static MapXerox instance;

    public MapXerox() {
        super(Categories.World, "map-xerox", "Helper module for automated map cloning commands.");
        instance = this;
    }

    @Override
    public void onActivate() {
        tasks.clear();
        waitMode = WaitMode.None;
    }

    public void newTask(XeroxTask t) {
        if (tasks.empty()) {
            ticks = 0;
        }
        tasks.push(t);
    }

    public void openInventory() {
        mc.gui.setScreen(new InventoryScreen(mc.player));
    }

    public void closeInventory() {
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> s) {
            s.onClose();
        }
    }

    public boolean verbose() {
        return verbose.get();
    }

    public boolean checkEmptyMaps() {
        return !skipMapCheck.get();
    }

    public void initiateInventoryCloning() {
        synchronized (this) {
            Vector<Integer> mapSlots = new Vector<>();
            int mapCount = 0;
            for (int i = 0; i < mc.player.inventoryMenu.slots.size(); ++i) {
                ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
                if (stack.get(DataComponents.MAP_ID) != null) {
                    mapSlots.add(i);
                    mapCount += stack.count();
                }
            }
            info("Found %d map(s) to clone.", mapCount);
            FindItemResult maps = InvUtils.find(Items.MAP);
            if ((!maps.found() || maps.count() < mapCount) && checkEmptyMaps()) {
                error("You don't have enough empty maps.");
            } else {
                newTask(new CloneMapsTask(mapSlots, true));
            }
        }
    }

    public void initiateBundleCloning() {
        synchronized (this) {
            waitMode = WaitMode.BundleCloning;
            anvil = null;
            info("Attack Anvil block used for renaming bundles. Attack anything other than an Anvil to skip renaming.");
        }
    }

    public void initiateShulkerCloning() {
        synchronized (this) {
            waitMode = WaitMode.ShulkerCloning;
            srcShulker = null;
            dstShulker = null;
            anvil = null;
            info("Attack Shulker Box containing maps to be cloned.");
        }
    }

    public void panic() {
        tasks.clear();
        throw new PanickedException();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (ticks == XeroxTask.UNTIL_CONTAINER_CONTENT_SET || ticks == XeroxTask.UNTIL_CONTAINER_DATA_SET) {
            if (tickPacket -- <= 0) {
                warning("Didn't get packet within timeout. Proceeding anyway.");
                ticks = 1;
            }
            return;
        }
        if (ticks -- <= 0) {
            if (!tasks.empty()) {
                try {
                    XeroxTask currentTask = tasks.peek();
                    int r = currentTask.tick();
                    switch (r) {
                        case XeroxTask.DEFAULT:
                            ticks = interactionRate.get();
                            break;
                        case XeroxTask.IMMEDIATE: ticks = 0;
                            break;
                        case XeroxTask.UNTIL_CONTAINER_CONTENT_SET:
                            ticks = XeroxTask.UNTIL_CONTAINER_CONTENT_SET;
                            tickPacket = 20;
                            break;
                        case XeroxTask.UNTIL_CONTAINER_DATA_SET:
                            ticks = XeroxTask.UNTIL_CONTAINER_DATA_SET;
                            tickPacket = 20;
                            break;
                        default:
                            ticks = r;
                    }
                    if (currentTask.isComplete()) {
                        tasks.pop();
                        if (tasks.empty())
                            info("Tasks finished.");
                    }
                } catch (PanickedException _p) {
                    error("Tasks failed.");
                }
            } else ticks = 0;
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundContainerSetContentPacket && ticks == XeroxTask.UNTIL_CONTAINER_CONTENT_SET) {
            ticks = 1;
        }
        if (event.packet instanceof ClientboundContainerSetDataPacket && ticks == XeroxTask.UNTIL_CONTAINER_DATA_SET) {
            ticks = 1;
        }
    }

    @EventHandler
    private void onStartBreakingBlockEvent(StartBreakingBlockEvent event) {
        if (mc.hitResult.getType() != HitResult.Type.BLOCK)
            return;
        BlockHitResult bh = (BlockHitResult) mc.hitResult;
        if (!event.blockPos.equals(bh.getBlockPos()))
            return;
        BlockState bs = mc.level.getBlockState(event.blockPos);
        synchronized (this) {
            if (waitMode == WaitMode.BundleCloning) {
                if (bs.getBlock() instanceof AnvilBlock) {
                    anvil = new BlockInteractState(bh, (double)mc.player.getXRot(), (double)mc.player.getYRot());
                } else {
                    anvil = null;
                }
                waitMode = WaitMode.None;
                if (createBundleCloningTasks())
                    info("Tasks created.");
            } else if (waitMode == WaitMode.ShulkerCloning) {
                if (srcShulker == null) {
                    if (bs.getBlock() instanceof ShulkerBoxBlock) {
                        srcShulker = new BlockInteractState(bh, (double)mc.player.getXRot(), (double)mc.player.getYRot());
                        info("Now, attack Shulker Box where the cloned maps should be stored.");
                    } else {
                        error("That was not a Shulker Box ...");
                    }
                } else if (dstShulker == null) {
                    if (bs.getBlock() instanceof  ShulkerBoxBlock) {
                        dstShulker = new BlockInteractState(bh, (double)mc.player.getXRot(), (double)mc.player.getYRot());
                        info("Attack Anvil block used for renaming bundles. Attack anything other than an Anvil to skip renaming.");
                    } else {
                        error("That was not a Shulker Box ...");
                    }
                } else {
                    if (bs.getBlock() instanceof AnvilBlock) {
                        anvil = new BlockInteractState(bh, (double)mc.player.getXRot(), (double)mc.player.getYRot());
                    } else {
                        anvil = null;
                    }
                    waitMode = WaitMode.None;
                    newTask(new CloneShulkerTask(srcShulker, dstShulker, anvil));
                    info("Tasks created.");
                }
            }
        }
    }

    private boolean createBundleCloningTasks() {
        Vector<Integer> slots = new Vector<>();
        int mapCount = 0;
        boolean invalid = false;
        for (int i = 0; i < mc.player.inventoryMenu.slots.size(); ++i) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
            BundleContents c = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (c != null && !c.items().isEmpty()) {
                var r = Utils.countMapsInBundle(c);
                invalid |= r.second();
                mapCount += r.first();
                slots.add(i);
            }
        }
        if (invalid) {
            error("At least one bundle contains something other than filled maps.");
            return false;
        }
        info("Found %d map(s) to clone.", mapCount);
        FindItemResult maps = InvUtils.find(Items.MAP);
        if ((!maps.found() || maps.count() < mapCount) && checkEmptyMaps()) {
            error("You don't have enough empty maps.");
            return false;
        }
        for (Integer slot : slots) {
            newTask(new CloneBundleTask(slot, anvil));
        }
        return true;
    }

    public enum WaitMode {
        None,
        //InventoryCloning,
        BundleCloning,
        ShulkerCloning,
    }

    public static class PanickedException extends RuntimeException {}

    public record BlockInteractState(BlockHitResult hit, double pitch, double yaw) {}

}
