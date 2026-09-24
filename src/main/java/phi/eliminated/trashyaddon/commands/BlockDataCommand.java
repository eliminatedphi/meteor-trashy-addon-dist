// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BlockDataCommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    public BlockDataCommand() {
        super("bd", "Dump client side data of targeted block.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                error("no block picked");
                return 100;
            }
            BlockHitResult bh = (BlockHitResult) mc.hitResult;
            BlockPos bp = bh.getBlockPos();
            BlockState bs = mc.level.getBlockState(bp);
            if (bs == null)
            {
                error("no block state");
                return 100;
            }
            info(Component.literal("block is ").append(bs.getBlock().getName()));
            BlockEntity be = mc.level.getBlockEntity(bp);
            if (be == null)
            {
                error("block has no block entity");
                return SINGLE_SUCCESS;
            }
            ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(be.problemPath(), LogUtils.getLogger());
            TagValueOutput tvo = TagValueOutput.createWithContext(reporter, be.getLevel().registryAccess());
            be.saveWithFullMetadata(tvo);
            info(NbtUtils.toPrettyComponent(tvo.buildResult()));

            return SINGLE_SUCCESS;
        });
    }
}
