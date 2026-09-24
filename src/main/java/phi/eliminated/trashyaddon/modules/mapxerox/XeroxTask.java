// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapxerox;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.ContainerInput;
import org.jetbrains.annotations.NotNull;

public abstract class XeroxTask {
    protected final Minecraft mc;
    public XeroxTask() {
        mc = Minecraft.getInstance();
    }
    // return -1 to skip ticking between interactions (next task will be executed in the following tick)
    // useful when no interaction was performed in this tick
    //
    public abstract int tick();
    public abstract boolean isComplete();

    public static final int DEFAULT = 0; // default behavior (wait for Interaction Rate number of client ticks before next task tick)
    public static final int IMMEDIATE = -1; // skip ticking between interactions (next task tick happens in the game tick that immediately follows)
    public static final int UNTIL_CONTAINER_CONTENT_SET = -2; // until next ClientboundContainerSetContentPacket is processed
    public static final int UNTIL_CONTAINER_DATA_SET = -3; // until next ClientboundContainerSetDataPacket is processed

    protected void info(String message, Object... args) {
        ChatUtils.infoPrefix("Map Xerox", message, args);
    }
    protected void warning(String message, Object... args) {
        ChatUtils.warningPrefix("Map Xerox", message, args);
    }
    protected void error(String message, Object... args) {
        ChatUtils.errorPrefix("Map Xerox", message, args);
    }
    @NotNull
    protected <T extends Screen> T assertScreen(Class<T> sc) {
        if (!sc.isInstance(mc.gui.screen())) {
            error("%s closed unexpectedly", sc.getName());
            MapXerox.instance.panic();
        }
        return sc.cast(mc.gui.screen());
    }

    protected void clickSlot(int containerId, int slot) {
        mc.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }
    protected void shiftClickSlot(int containerId, int slot) {
        mc.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
    }
    protected void secondaryClickSlot(int containerId, int slot) {
        mc.gameMode.handleContainerInput(containerId, slot, 1, ContainerInput.PICKUP, mc.player);
    }
}
