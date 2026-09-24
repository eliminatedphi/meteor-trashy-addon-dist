// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import phi.eliminated.trashyaddon.modules.AntiInventoryMove;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    void containerInputPre(final int containerId, final int slotNum, final int buttonNum, final ContainerInput containerInput, final Player player, CallbackInfo ci) {
        AntiInventoryMove aim = Modules.get().get(AntiInventoryMove.class);
        if (aim != null) aim.cancelMovementNow();
    }
}
