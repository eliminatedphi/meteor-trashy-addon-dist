// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.ESP;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import phi.eliminated.trashyaddon.modules.MapmanIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin <T extends ItemFrame> extends EntityRenderer<T, ItemFrameRenderState> {
    private static Optional<Boolean> fabricRenderingApiFound = Optional.empty();

    protected ItemFrameRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "shouldShowName", at = @At(value = "HEAD"), cancellable = true)
    protected void shouldShowName(final T entity, final double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (!Modules.get().get(MapmanIntegration.class).itemFrameIdRender()) return;
        if (!Minecraft.getInstance().gui.hud.isHidden() && this.entityRenderDispatcher.crosshairPickEntity == entity && entity.getItem().get(DataComponents.MAP_ID) != null)
            cir.setReturnValue(true);
    }

    @Inject(method = "getNameTag", at = @At(value = "HEAD"), cancellable = true)
    protected void getNameTag(final T entity, CallbackInfoReturnable<Component> cir) {
        if (!Modules.get().get(MapmanIntegration.class).itemFrameIdRender()) return;
        ItemStack s = entity.getItem();
        MapId id = s.get(DataComponents.MAP_ID);
        Component n = s.getCustomName();
        if (n == null && id != null) {
            cir.setReturnValue(Component.literal(String.format("map#%d", id.id())).withStyle(ChatFormatting.AQUA));
        }
    }

    @Inject(method = "extractRenderState", at = @At(value = "TAIL"))
    public void extractRenderState(final T entity, final ItemFrameRenderState state, final float partialTicks, CallbackInfo ci) {
        if (fabricRenderingApiFound.isEmpty()) {
            try {
                Class<?> ignored = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.FabricRenderPipeline");
                fabricRenderingApiFound = Optional.of(true);
            } catch (ClassNotFoundException e) {
                fabricRenderingApiFound = Optional.of(false);
            }
        }
        if (fabricRenderingApiFound.get()) {
            ESP esp = Modules.get().get(ESP.class);
            if (esp != null && esp.isActive()) {
                Color c = esp.getColor(entity);
                if (!esp.shouldSkip(entity) && c != null)
                    state.outlineColor = c.getPacked();
            }
        }
    }
}
