// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import phi.eliminated.trashyaddon.modules.SlotHighlight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
    public AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void onExtractSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        int color = Modules.get().get(SlotHighlight.class).getSlotColor(slot.index).getPacked();
        if (color != 0) context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }
}
