package phi.eliminated.trashyaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import phi.eliminated.trashyaddon.modules.MapmanIntegration;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BACommand extends Command {
    private final Minecraft mc = Minecraft.getInstance();
    private final MapmanIntegration ba;
    public BACommand() {
        super("ba", "Command interface to Bibliotheca Arcana");
        ba = Modules.get().get(MapmanIntegration.class);
        assert(ba != null);
    }
    @Nullable
    private ItemFrame getSelectedItemFrame() {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.ENTITY) {
            return null;
        }
        EntityHitResult eh = (EntityHitResult) mc.hitResult;
        Entity e = eh.getEntity();
        if (e instanceof ItemFrame ife) {
            return ife;
        } else return null;
    }
    private void createGroupInner() {
        if (mc.level == null) return;
        ItemFrame tl = ba.getTopLeftForGroup();
        ItemFrame tr = ba.getTopRightForGroup();
        ItemFrame br = ba.getBottomRightForGroup();
        assert(tl != null && tr != null && br != null);
        if (tl.getDirection() != tr.getDirection() || tl.getDirection() != br.getDirection()) {
            error("Item frames are facing different directions.");
            return;
        }
        BlockPos tlp = tl.getPos();
        BlockPos brp = br.getPos();
        BlockPos trp = tr.getPos();
        BoundingBox bb = BoundingBox.fromCorners(tlp, brp);
        if (!bb.isInside(trp)) {
            error("Invalid selection. (top right out of bounds?)");
            return;
        }
        HashMap<BlockPos, ItemFrame> frames = new HashMap<>();
        for (Entity e : mc.level.getEntities().getAll()) {
            if (e instanceof ItemFrame ife) {
                BlockPos p = ife.getPos();
                if (ife.getDirection().equals(tl.getDirection()) && bb.isInside(p)) {
                    frames.put(p, ife);
                }
            }
        }
        Vec3i wdiff = trp.subtract(tlp);
        Vec3i wstep = new Vec3i(Mth.sign(wdiff.getX()), Mth.sign(wdiff.getY()), Mth.sign(wdiff.getZ()));
        if (wstep.distSqr(Vec3i.ZERO) != 1) {
            error("Invalid selection. (bad vertical step: %s)", wstep.toShortString());
            return;
        }
        Optional<Direction.Axis> waxisopt = Arrays.stream(Direction.Axis.VALUES).filter(a -> Mth.abs(wstep.get(a)) == 1).findFirst();
        if (waxisopt.isEmpty()) {
            error("Invalid selection. Also this shouldn't happen...");
            return;
        }
        Direction.Axis waxis = waxisopt.get();
        int w = Mth.abs(wdiff.get(waxis)) + 1;

        Vec3i hdiff = brp.subtract(trp);
        Vec3i hstep = new Vec3i(Mth.sign(hdiff.getX()), Mth.sign(hdiff.getY()), Mth.sign(hdiff.getZ()));
        if (hstep.distSqr(Vec3i.ZERO) != 1) {
            error("Invalid selection. (bad horizontal step: %s)", hstep.toShortString());
            return;
        }
        Optional<Direction.Axis> haxisopt = Arrays.stream(Direction.Axis.VALUES).filter(a -> Mth.abs(hstep.get(a)) == 1).findFirst();
        if (haxisopt.isEmpty()) {
            error("Invalid selection. Also this shouldn't happen...");
            return;
        }
        Direction.Axis haxis = haxisopt.get();
        int h = Mth.abs(hdiff.get(haxis)) + 1;

        Vector<ItemFrame> frameList = new Vector<>();
        for (int i = 0; i < h; ++i)
            for (int j = 0; j < w; ++j) {
                BlockPos p = tlp.offset(hstep.multiply(i)).offset(wstep.multiply(j));
                if (frames.containsKey(p))
                    frameList.add(frames.get(p));
            }
        if (frameList.size() != w * h) {
            error("Invalid selection. (incorrect number of maps: %d vs %d x %d)", frameList.size(), w, h);
            return;
        }
        List<Integer> idList = frameList.stream().map(ife -> {
            MapId id = ife.getFramedMapId(ife.getItem());
            return id == null ? -1 : id.id();
        }).toList();
        info("Found a %d x %d area with %d maps", w, h, idList.size());
        ba.setGroup(w, h, idList);
    }
    private void tryCreateGroup() {
        ItemFrame tl = ba.getTopLeftForGroup();
        ItemFrame tr = ba.getTopRightForGroup();
        ItemFrame br = ba.getBottomRightForGroup();
        if (tl == null || tr == null || br == null)
            return;
        info("Creating group.");
        createGroupInner();
        ba.setTopLeftForGroup(null);
        ba.setTopRightForGroup(null);
        ba.setBottomRightForGroup(null);
    }
    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(
            literal("send-group")
                .then(literal("top-left").executes(ctx -> {
                    if (!ba.isActive()) {
                        error("BA Integration module must be active.");
                        return 100;
                    }
                    ItemFrame ife = getSelectedItemFrame();
                    if (ife == null) {
                        error("You must be looking at an item frame.");
                        return 100;
                    }
                    ba.setTopLeftForGroup(ife);
                    info("Top left corner set.");
                    tryCreateGroup();
                    return SINGLE_SUCCESS;
                }))
                .then(literal("top-right").executes(ctx -> {
                    if (!ba.isActive()) {
                        error("BA Integration module must be active.");
                        return 100;
                    }
                    ItemFrame ife = getSelectedItemFrame();
                    if (ife == null) {
                        error("You must be looking at an item frame.");
                        return 100;
                    }
                    ba.setTopRightForGroup(ife);
                    info("Top right corner set.");
                    tryCreateGroup();
                    return SINGLE_SUCCESS;
                }))
                .then(literal("bottom-right").executes(ctx -> {
                    if (!ba.isActive()) {
                        error("BA Integration module must be active.");
                        return 100;
                    }
                    ItemFrame ife = getSelectedItemFrame();
                    if (ife == null) {
                        error("You must be looking at an item frame.");
                        return 100;
                    }
                    ba.setBottomRightForGroup(ife);
                    info("Bottom right corner set.");
                    tryCreateGroup();
                    return SINGLE_SUCCESS;
                }))
        );
    }
}
