// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import phi.eliminated.trashyaddon.commands.argument.CHexColorArgument;
import phi.eliminated.trashyaddon.modules.MapmanIntegration;

/*
Usage:
mih <id> [color]  :  highlight item frames with map #<id> using [color] (defaults to bright magenta)
Note: Use .mih -1 to clear highlighting
 */

public class MapItemHighlightCommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    public MapItemHighlightCommand() {
        super("mih", "Highlight inventory items that either is or contains map with id.");
    }

    private void exec(int id, Color color) {
        info("highlighting map with id " + id + " " + color.toString());
        if (id == -1) {
            MapmanIntegration.instance.clearSlotHighlight();
            MapmanIntegration.instance.clearFrameHighlight();
        } else {
            MapmanIntegration.instance.setSlotHighlightForMap(id, color);
            MapmanIntegration.instance.setFrameHighlightForMap(id, color);
        }
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("id", IntegerArgumentType.integer()).executes(ctx -> {
            int targetID = IntegerArgumentType.getInteger(ctx, "id");
            Color color = new Color(0xffffff7f);
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
