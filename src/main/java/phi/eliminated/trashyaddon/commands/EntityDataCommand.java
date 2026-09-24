// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class EntityDataCommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    public EntityDataCommand() {
        super("ed", "Erectile Dysfunction /s (Dump client side data of targeted entity.)");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            //info("erectile dysfunction start");
            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) {
                error("no entity found");
                return 100;
            }
            EntityHitResult eh = (EntityHitResult) mc.hitResult;
            Entity e = eh.getEntity();
            info(Component.literal("entity is ").append(e.getType().getDescription()));
            ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(e.problemPath(), LogUtils.getLogger());
            TagValueOutput tvo = TagValueOutput.createWithContext(reporter, e.registryAccess());
            e.save(tvo);
            info(NbtUtils.toPrettyComponent(tvo.buildResult()));

            return SINGLE_SUCCESS;
        });
    }
}
