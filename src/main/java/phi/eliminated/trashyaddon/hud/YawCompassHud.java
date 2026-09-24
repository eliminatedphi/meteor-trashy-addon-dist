// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.hud;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class YawCompassHud extends HudElement {
    public static final HudElementInfo<YawCompassHud> INFO = new HudElementInfo<>(Hud.GROUP, "yaw-compass", "A linear horizontal compass HUD", YawCompassHud::new);
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> hudWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("hud-width")
        .description("HUD width as percentage of window width")
        .defaultValue(0.8)
        .min(0.01)
        .max(1.)
        .sliderRange(0.1, 1.)
        .build()
    );
    private final Setting<Double> ticksHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("ticks-height")
        .description("Height of compass ticks")
        .defaultValue(16)
        .min(4)
        .max(64)
        .sliderRange(4, 64)
        .build()
    );
    private final Setting<Double> ticksThickness = sgGeneral.add(new DoubleSetting.Builder()
        .name("ticks-thickness")
        .description("Thickness of compass ticks")
        .defaultValue(1)
        .min(0.25)
        .max(10)
        .sliderRange(0.25, 10)
        .build()
    );
    private final Setting<Boolean> useFov = sgGeneral.add(new BoolSetting.Builder()
        .name("use-fov")
        .description("Use FOV as span of the compass")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> span = sgGeneral.add(new DoubleSetting.Builder()
        .name("span")
        .description("Span of the compass")
        .defaultValue(60)
        .min(30)
        .max(120)
        .sliderRange(30, 120)
        .visible(() -> !useFov.get())
        .build()
    );
    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Text scale")
        .defaultValue(1)
        .min(0.25)
        .sliderRange(0.25, 5)
        .build()
    );
    private final Setting<LabelType> labelType = sgGeneral.add(new EnumSetting.Builder<LabelType>()
        .name("label-type")
        .description("How the compass should be labelled")
        .defaultValue(LabelType.Direction)
        .build()
    );
    private final Setting<Boolean> keepCentered = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-centered")
        .description("Keep HUD centered horizontally")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> invertLayout = sgGeneral.add(new BoolSetting.Builder()
        .name("invert-layout")
        .description("Flip the layout of the compass upside down")
        .defaultValue(false)
        .build()
    );
    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the HUD")
        .defaultValue(new SettingColor(225, 225, 225))
        .build()
    );
    private final Minecraft mc;

    public YawCompassHud() {
        super(INFO);
        mc = Minecraft.getInstance();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (keepCentered.get())
            this.x = (int) (mc.getWindow().getWidth() * (1 - hudWidth.get()) / 2);
        setSize(mc.getWindow().getWidth() * hudWidth.get(), ticksHeight.get() * 1.5 + 4 + 1.8 * renderer.textHeight(true, getTextScale()));
        double yaw = mc.player == null ? 0 : Mth.wrapDegrees(mc.player.getYRot());
        double span = useFov.get() ? mc.options.fov().get() : this.span.get();
        double mina = yaw - span;
        double maxa = yaw + span;
        double ticksy = y;
        double ticksly = ticksy + ticksHeight.get() * 1.5/* + 2*/;
        double yawty = ticksly/* + 2*/ + 0.8 * renderer.textHeight(true, getTextScale());
        if (invertLayout.get()) {
            yawty = y;
            ticksly = yawty + renderer.textHeight(true, getTextScale())/* + 2*/;
            ticksy = ticksly/* + 2*/ + 0.8 * renderer.textHeight(true, getTextScale());
        }
        int ltick = (int)(Math.abs(mina) / 7.5) * Mth.sign(mina);
        double ppt = getWidth() / (span * 2 / 7.5);
        double center = this.x + getWidth() / 2.;
        for (int t = ltick; t * 7.5 <= maxa; ++t) {
            double p = (t * 7.5 - yaw) / 7.5 * ppt + center;
            drawTick(renderer, p, ticksy, t % 3 == 0 ? 1.5 : 1);
            drawCenteredText(renderer, labelForTick(t), p, ticksly, color.get(), true, 0.8);
        }
        drawCenteredText(renderer, String.format("%.1f", yaw), this.x + getWidth() / 2., yawty, color.get(), true, 1);
    }

    private void drawTick(HudRenderer renderer, double x, double y, double h) {
        double thickness = ticksThickness.get();
        renderer.quad(x - thickness / 2, y, thickness, h * ticksHeight.get(), color.get());
    }

    private void drawCenteredText(HudRenderer renderer, String text, double x, double y, Color color, boolean shadow, double rscale) {
        double w = renderer.textWidth(text, shadow, rscale * getTextScale());
        renderer.text(text, x - w / 2, y, color, shadow, rscale * getTextScale());
    }

    private double getTextScale() { return textScale.get() * Hud.get().getTextScale(); }

    // 7.5° increments
    private String labelForTick(int tick) {
        int rtick = tick % 48;
        while (rtick <= -24) rtick += 48;
        while (rtick > 24) rtick -= 48;
        if (labelType.get() == LabelType.Axes) {
            return switch (rtick) {
                case -24, 24 -> "-z";
                case -18     -> "+-";
                case -12     -> "+x";
                case -6      -> "++";
                case 0       -> "+z";
                case 6       -> "-+";
                case 12      -> "-x";
                case 18      -> "--";
                default      -> "";
            };
        } else if (labelType.get() == LabelType.Direction) {
            return switch (rtick) {
                case -24, 24 -> "N";
                case -18     -> "NE";
                case -12     -> "E";
                case -6      -> "SE";
                case 0       -> "S";
                case 6       -> "SW";
                case 12      -> "W";
                case 18      -> "NW";
                default      ->  "";
            };
        } else if (labelType.get() == LabelType.Bearing) {
            if (rtick % 2 == 0)
                return String.format("%.1f", tick * 7.5);
            else return "";
        }
        return "";
    }

    public enum LabelType {
        Direction,
        Axes,
        Bearing
    }
}
