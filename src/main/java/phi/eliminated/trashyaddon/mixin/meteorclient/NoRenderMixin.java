// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.mixin.meteorclient;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import phi.eliminated.trashyaddon.modules.extensions.NoRenderExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoRender.class)
public abstract class NoRenderMixin extends Module implements NoRenderExtension {
    public NoRenderMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Shadow
    private final SettingGroup sgWorld = settings.getGroup("World");

    @Unique
    private final Setting<Boolean> endFlash = sgWorld.add(new BoolSetting.Builder()
        .name("end-flash")
        .description("Disables the stupid client-side effect that is the end flash completely, including the sounds.")
        .defaultValue(false)
        .build()
    );

    @Unique
    public boolean meteor_trashy_addon$getEndFlashDisabled() {
        return isActive() && endFlash.get();
    }
}
