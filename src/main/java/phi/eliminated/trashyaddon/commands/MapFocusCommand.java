package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import phi.eliminated.trashyaddon.modules.MapmanIntegration;

public class MapFocusCommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    public MapFocusCommand() { super("mf", "Scroll to a map inside BA."); }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (!MapmanIntegration.instance.isActive()) {
                error("The BA Integration module must be active.");
                return 100;
            }
            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) {
                error("No entity found.");
                return 100;
            }
            EntityHitResult eh = (EntityHitResult) mc.hitResult;
            Entity e = eh.getEntity();
            if (e instanceof ItemFrame ife) {
                ItemStack s = ife.getItem();
                MapId id = s.get(DataComponents.MAP_ID);
                if (id == null) {
                    error("Item frame does not contain a filled map.");
                    return 100;
                }
                MapmanIntegration.instance.focusMap(id);
            } else {
                error("Not looking at item frame.");
                return 100;
            }
            return SINGLE_SUCCESS;
        });
    }
}
