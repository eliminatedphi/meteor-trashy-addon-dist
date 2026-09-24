// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import phi.eliminated.trashyaddon.commands.argument.CEntityArgument;
import phi.eliminated.trashyaddon.commands.argument.CEntitySelector;
import phi.eliminated.trashyaddon.commands.argument.CHexColorArgument;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/*
Usage:
eh <target selector> set <color>  :  Highlight targeted entities with specified color
eh <target selector> remove       :  Remove highlight from targeted entities
eh clear                          :  Remove highlight from all entities
Note: This command requires the ESP module to function.
 */

public class EntityHighlightCommand extends Command {
    private final static HashMap<UUID, Color> entityColors = new HashMap<>();

    public static void clearColors() { entityColors.clear(); }
    public static void setEntityColor(Entity e, Color c) {
        entityColors.put(e.getUUID(), c);
    }
    public static void setEntityColors(List<? extends Entity> entities, Color c) {
        for (Entity e : entities)
            setEntityColor(e, c);
    }
    public static void removeEntityColor(Entity e) {
        entityColors.remove(e.getUUID());
    }
    public static void removeEntityColors(List<? extends Entity> entities) {
        for (Entity e : entities)
            removeEntityColor(e);
    }
    public static Color getEntityColor(Entity e) {
        return entityColors.get(e.getUUID());
    }

    public EntityHighlightCommand() {
        super("eh", "Highlight selected entities.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(
            argument("match", CEntityArgument.entities()).then(
                literal("set").then(
                    argument("color", CHexColorArgument.hexColor()).executes(ctx -> {
                        CEntitySelector es = ctx.getArgument("match", CEntitySelector.class);
                        FabricClientCommandSource fccs = (FabricClientCommandSource) new ClientSuggestionProvider(mc.getConnection(), mc, PermissionSet.ALL_PERMISSIONS);
                        List<? extends Entity> entities = es.findEntities(fccs);
                        Integer col = ctx.getArgument("color", Integer.class);
                        setEntityColors(entities, new Color(col));
                        info("Successfully set highlight for " + entities.size() + " entity(ies).");
                        return SINGLE_SUCCESS;
                    }
                ))).then(
                literal("remove").executes(ctx -> {
                    CEntitySelector es = ctx.getArgument("match", CEntitySelector.class);
                    FabricClientCommandSource fccs = (FabricClientCommandSource) new ClientSuggestionProvider(mc.getConnection(), mc, PermissionSet.ALL_PERMISSIONS);
                    List<? extends Entity> entities = es.findEntities(fccs);
                    removeEntityColors(entities);
                    info("Successfully removed highlight for " + entities.size() + " entity(ies).");
                    return SINGLE_SUCCESS;
                })
            )
        ).then(
            literal("clear").executes(ctx -> {
                clearColors();
                info("Successfully removed highlight for all entities.");
                return SINGLE_SUCCESS;
            })
        );
    }
}
