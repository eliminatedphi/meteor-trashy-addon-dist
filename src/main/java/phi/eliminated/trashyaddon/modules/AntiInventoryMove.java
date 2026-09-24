// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

public class AntiInventoryMove extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> nTicks = sgGeneral.add(new IntSetting.Builder()
        .name("Ticks blocked")
        .description("Block player movement for this many ticks after the interaction.")
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );
    private int tick;

    public AntiInventoryMove() {
        super(Categories.Movement, "anti-inventory-move", "Momentarily blocks player movement when inventory interaction happens.");
    }

    public boolean shouldBlock() {
        return isActive() && tick > 0;
    }

    public void cancelMovementNow() {
        tick = nTicks.get();
        if (mc.getConnection() != null && mc.player != null) {
            mc.getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
            if (mc.player.isSprinting()) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                mc.player.setSprinting(false);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (tick > 0) --tick;
    }
}
