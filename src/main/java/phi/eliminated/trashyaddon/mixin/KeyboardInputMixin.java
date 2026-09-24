// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import phi.eliminated.trashyaddon.modules.AntiInventoryMove;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"))
    private Input overrideInput(Input o) {
        AntiInventoryMove aim = Modules.get().get(AntiInventoryMove.class);
        if (aim != null && aim.shouldBlock()) {
            return Input.EMPTY;
        }
        return o;
    }
}
