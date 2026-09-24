// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Style;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import phi.eliminated.trashyaddon.commands.argument.CEntityArgument;
import phi.eliminated.trashyaddon.commands.argument.CEntitySelector;
import phi.eliminated.trashyaddon.commands.argument.CHexColorArgument;

import java.util.Formatter;
import java.util.List;
import java.util.stream.Stream;

/*
Usage:
ml <id> [color]  :  highlight item frames with map #<id> using [color] (defaults to bright magenta)
Note: Use .eh clear to remove highlight from all item frames.
 */

public class MapLocateCommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    public MapLocateCommand() {
        super("ml", "Locate item frames with a specific map.");
    }

    private void exec(int id, Color color) {
        try {
            CEntityArgument dummyArgIF = CEntityArgument.entities();
            CEntitySelector esIF = dummyArgIF.parse(new StringReader(new Formatter().format("@e[type=minecraft:item_frame,nbt={Item:{components:{\"minecraft:map_id\":%d}}}]", id).toString()));
            CEntityArgument dummyArgGIF = CEntityArgument.entities();
            CEntitySelector esGIF = dummyArgGIF.parse(new StringReader(new Formatter().format("@e[type=minecraft:glow_item_frame,nbt={Item:{components:{\"minecraft:map_id\":%d}}}]", id).toString()));
            FabricClientCommandSource fccs = (FabricClientCommandSource) new ClientSuggestionProvider(mc.getConnection(), mc, PermissionSet.ALL_PERMISSIONS);
            List<? extends Entity> l1 = esIF.findEntities(fccs);
            List<? extends Entity> l2 = esGIF.findEntities(fccs);
            if (l1.isEmpty() && l2.isEmpty()) {
                info(Component.literal(new Formatter().format("Map #%d not found", id).toString()).setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                return;
            }
            info(new Formatter().format("Map #%d found at:", id).toString());
            Stream.concat(l1.stream(), l2.stream()).forEach(e -> {
                EntityHighlightCommand.setEntityColor(e, color);
                info(new Formatter().format("  %.1f %.1f %.1f", e.position().x, e.position().y, e.position().z).toString());
            });
        } catch (CommandSyntaxException e) {
            error("This should never happen. The author of this garbage is probably an idiot.");
        }
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("id", IntegerArgumentType.integer()).executes(ctx -> {
            int targetID = IntegerArgumentType.getInteger(ctx, "id");
            Color color = new Color(0xffff7fff);
            exec(targetID, color);
            return SINGLE_SUCCESS;
        }).then(argument("color", CHexColorArgument.hexColor()).executes(ctx -> {
            int targetID = IntegerArgumentType.getInteger(ctx, "id");
            Integer col = ctx.getArgument("color", Integer.class);
            exec(targetID, new Color(col));
            return SINGLE_SUCCESS;
        })));
    }
}
