// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import phi.eliminated.trashyaddon.modules.mapxerox.MapXerox;

public class MapXeroxCommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    public MapXeroxCommand() {
        super("mx", "Invoke Map Xerox.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("inventory").executes(ctx -> {
            if (!MapXerox.instance.isActive()) {
                error("The Map Xerox module must be active.");
                return SINGLE_SUCCESS;
            }
            MapXerox.instance.initiateInventoryCloning();
            return SINGLE_SUCCESS;
        })).then(literal("bundle").executes(ctx -> {
            if (!MapXerox.instance.isActive()) {
                error("The Map Xerox module must be active.");
                return SINGLE_SUCCESS;
            }
            MapXerox.instance.initiateBundleCloning();
            return SINGLE_SUCCESS;
        })).then(literal("shulker").executes(ctx -> {
            if (!MapXerox.instance.isActive()) {
                error("The Map Xerox module must be active.");
                return SINGLE_SUCCESS;
            }
            MapXerox.instance.initiateShulkerCloning();
            return SINGLE_SUCCESS;
        }));
        builder.executes(context -> {
            info("Command interface for the Map Xerox module.");
            info("Try autocomplete on me!");
            info("Note: the Map Xerox module must be active for these commands to work.");
            return SINGLE_SUCCESS;
        });
    }
}
