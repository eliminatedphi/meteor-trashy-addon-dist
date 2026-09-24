// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.hud;

import it.unimi.dsi.fastutil.Pair;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.*;
import meteordevelopment.meteorclient.systems.hud.elements.ActiveModulesHud;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

public class EntityCountHud extends HudElement {
    public static final HudElementInfo<EntityCountHud> INFO = new HudElementInfo<>(Hud.GROUP, "entity-count", "Count entities in view", EntityCountHud::new);
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Set<EntityType<?>>> eTypes = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entity-types")
        .description("List entity of these types")
        .build()
    );
    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines")
        .description("How many lines to show at max.")
        .defaultValue(8)
        .min(1)
        .max(64)
        .sliderRange(1, 24)
        .build()
    );
    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment")
        .description("Horizontal alignment.")
        .defaultValue(Alignment.Auto)
        .build()
    );
    private final Setting<Boolean> customScale = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-scale")
        .description("Applies a custom scale to this hud element.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Custom scale.")
        .visible(customScale::get)
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );
    private final Setting<SettingColor> ecolor = sgGeneral.add(new ColorSetting.Builder()
        .name("entity-color")
        .description("Entity name color")
        .defaultValue(new SettingColor(225, 25, 225))
        .build()
    );
    private final Setting<SettingColor> ccolor = sgGeneral.add(new ColorSetting.Builder()
        .name("number-color")
        .description("Entity count color")
        .defaultValue(new SettingColor(175, 175, 175))
        .build()
    );
    private final Vector<Pair<EntityType<?>, Integer>> ec = new Vector<>();
    private final Minecraft mc;

    public EntityCountHud() {
        super(INFO);
        mc = Minecraft.getInstance();
    }

    @Override
    public void tick(HudRenderer renderer) {
        HashMap<EntityType<?>, Integer> ecount = new HashMap<>();
        if (mc.level == null) {
            ec.clear();
            return;
        }
        for (Entity e : mc.level.getEntities().getAll()) {
            EntityType<?> etype = e.getType();
            if (!eTypes.get().contains(etype)) continue;
            if (ecount.containsKey(etype)) {
                ecount.put(etype, ecount.get(etype) + 1);
            } else {
                ecount.put(etype, 1);
            }
        }
        ec.clear();
        ecount.forEach((k, v) -> ec.add(Pair.of(k, v)));
        ec.sort((a, b) -> Integer.compare(b.second(), a.second()));
        double w = ec.stream().map(p ->
            renderer.textWidth(p.first().getDescription().getString() + " x " + p.second().toString(), true, getScale()))
            .max(Double::compareTo).orElse(0.);
        setSize(w, Integer.min(ec.size(), maxLines.get()) * (renderer.textHeight(true, getScale()) + 1 * getScale()));
    }

    @Override
    public void render(HudRenderer renderer) {
        if (isInEditor()) {
            int w = Math.max(getWidth(), 20);
            int h = Math.max(getHeight(), (int) (renderer.textHeight(true, getScale()) + 1 * getScale()));
            setSize(w, h);
            renderer.text("Entity Count", x, y, ecolor.get(), true, getScale());
        }
        int c = Integer.min(ec.size(), maxLines.get());
        for (int i = 0; i < c; ++i) {
            Pair<EntityType<?>, Integer> p = ec.get(i);
            String ets = p.first().getDescription().getString();
            double a = alignX(renderer.textWidth(ets + " x " + p.second().toString(), true, getScale()), alignment.get());
            double x = this.x + a;
            double y = this.y + i * (renderer.textHeight(true, getScale()) + 1 * getScale());
            renderer.text(ets, x, y, ecolor.get(), true, getScale());
            renderer.text(" x " + p.second().toString(), x + renderer.textWidth(ets, true, getScale()), y, ccolor.get(), true, getScale());
        }
    }

    private double getScale() {
        return customScale.get() ? scale.get() : Hud.get().getTextScale();
    }
}
