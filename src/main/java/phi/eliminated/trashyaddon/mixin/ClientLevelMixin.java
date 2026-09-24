// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import phi.eliminated.trashyaddon.modules.extensions.NoRenderExtension;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin extends Level {
    protected ClientLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/ClientLevel;endFlashState:Lnet/minecraft/client/renderer/EndFlashState;", ordinal = 0, opcode = Opcodes.GETFIELD))
    private EndFlashState overrideEndFlash(EndFlashState o) {
        if (((NoRenderExtension) Modules.get().get(NoRender.class)).meteor_trashy_addon$getEndFlashDisabled())
            return null;
        else return o;
    }

    @Inject(method = "endFlashState", at = @At(value = "HEAD"), cancellable = true)
    public void getEndFlashState(CallbackInfoReturnable<EndFlashState> cir) {
        if (((NoRenderExtension) Modules.get().get(NoRender.class)).meteor_trashy_addon$getEndFlashDisabled())
            cir.setReturnValue(null);
    }
}
