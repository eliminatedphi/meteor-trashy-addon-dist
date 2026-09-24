// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.mixin.meteorclient;

import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.entity.Entity;
import phi.eliminated.trashyaddon.commands.EntityHighlightCommand;
import phi.eliminated.trashyaddon.modules.MapmanIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(meteordevelopment.meteorclient.systems.modules.render.ESP.class)
public abstract class ESPMixin {
    @Inject(method = "getColor", at = @At(value = "HEAD"), cancellable = true)
    private void getColor(Entity entity, CallbackInfoReturnable<Color> cir) {
        Color c = EntityHighlightCommand.getEntityColor(entity);
        if (c != null) {
            cir.setReturnValue(c);
        } else {
            c = MapmanIntegration.instance.getItemFrameHighlight(entity);
            if (c != null) {
                cir.setReturnValue(c);
            }
        }
    }
    @Inject(method = "shouldSkip", at = @At(value = "HEAD"), cancellable = true)
    private void shouldSkip(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (EntityHighlightCommand.getEntityColor(entity) != null || MapmanIntegration.instance.getItemFrameHighlight(entity) != null) {
            cir.setReturnValue(false);
        }
    }
}
