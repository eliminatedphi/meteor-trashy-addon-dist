// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.StringSplitter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.network.chat.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Reimplementation of https://github.com/zabi94/NBTTooltip from scratch
public class NBTTooltip extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Keybind> activateKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("Activation key")
        .description("Hold this key show component data. Unbind to show all the time.")
        .defaultValue(Keybind.fromKey(InputConstants.KEY_LCONTROL))
        .build()
    );
    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("Max lines")
        .description("Max amount of lines to show at once.")
        .min(0)
        .max(20)
        .defaultValue(4)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Keybind> fastScrollKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("Fast scroll key")
        .description("Hold this key to make scrolling faster")
        .defaultValue(Keybind.fromKey(InputConstants.KEY_LALT))
        .build()
    );
    private final Setting<Integer> normalScrollDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Normal scrolling rate")
        .description("Scroll one line every this many ticks.")
        .min(0)
        .max(100)
        .defaultValue(40)
        .sliderRange(0, 100)
        .build()
    );
    private final Setting<Integer> fastScrollDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Fast scrolling rate")
        .description("Scroll one line every this many ticks in fast scroll mode.")
        .min(0)
        .max(100)
        .defaultValue(10)
        .sliderRange(0, 100)
        .build()
    );
    private final Setting<Boolean> includeDefaultValues = sgGeneral.add(new BoolSetting.Builder()
        .name("Include default values")
        .description("Include default component values too.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> showDelimiters = sgGeneral.add(new BoolSetting.Builder()
        .name("Show delimiters")
        .description("Show delimiters around nbt data.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Keybind> scrollUpKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("Scroll up key")
        .description("Key binding for scrolling up.")
        .defaultValue(Keybind.fromKey(InputConstants.KEY_PAGEUP))
        .build()
    );
    private final Setting<Keybind> scrollDownKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("Scroll down key")
        .description("Key binding for scrolling down.")
        .defaultValue(Keybind.fromKey(InputConstants.KEY_PAGEDOWN))
        .build()
    );
    private final Setting<Keybind> copyKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("Copy to clipboard key")
        .description("Press this key to copy component data to clipboard.")
        .defaultValue(Keybind.fromKeys(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_C), Keybind.Modifier.CONTROL))
        .build()
    );

    ItemStack s;
    List<Component> lines;
    Component nbtComp;
    int currentLine = 0;
    int scrollTick = 0;
    boolean keyRepeat = false;
    boolean upIsPressed = false;
    boolean downIsPressed = false;

    public NBTTooltip() {
        super(Categories.Misc, "nbt-tooltip", "Show data components in item tooltips.");
    }

    private void updateNBT() {
        if (mc.level == null) return;
        ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(new ProblemReporter.FieldPathElement(""), LogUtils.getLogger());
        TagValueOutput tvo = TagValueOutput.createWithContext(reporter, mc.level.registryAccess());
        DataComponentMap m = s.getComponents();
        CompoundTag c = null;
        if (includeDefaultValues.get()) {
            if (m.isEmpty()) {
                lines = null;
                return;
            }
            tvo.store("item", DataComponentMap.CODEC, m);
            CompoundTag t = tvo.buildResult();
            Optional<CompoundTag> r = t.getCompound("item");
            if (r.isPresent()) c = r.get();
        } else {
            if (m instanceof PatchedDataComponentMap pdcm) {
                if (pdcm.asPatch().isEmpty()) {
                    lines = null;
                    return;
                }
                tvo.store("item", DataComponentPatch.CODEC, pdcm.asPatch());
                CompoundTag t = tvo.buildResult();
                Optional<CompoundTag> r = t.getCompound("item");
                if (r.isPresent()) c = r.get();
            } else { lines = null; return; }
        }
        if (c != null) {
            nbtComp = new TextComponentTagVisitor(" ").visit(c);
            lines = new StringSplitter((_c, _s) -> 1).splitLines(nbtComp, 40, Style.EMPTY).stream()
                .<Component>mapMulti((cc, con) -> {
                    MutableComponent comp = Component.literal("");
                    cc.visit(new FormattedText.StyledContentConsumer<Void>() {
                        @Override
                        public @NotNull Optional<Void> accept(@NotNull Style style, @NotNull String contents) {
                            comp.append(Component.literal(contents).withStyle(style));
                            return Optional.empty();
                        }
                    }, Style.EMPTY);
                    con.accept(comp);
                })
                .collect(Collectors.toList());
            currentLine = 0;
            scrollTick = 0;
        }
    }

    @EventHandler
    private void appendTooltip(ItemStackTooltipEvent event) {
        if (event.itemStack() != s) { // deliberate identical object test
            s = event.itemStack();
            updateNBT();
        }
        if (lines == null || lines.isEmpty())
            return;
        if (copyKey.get().isPressed()) {
            if (!keyRepeat) {
                mc.keyboardHandler.setClipboard(nbtComp.getString());
                info("Data component copied.");
            }
            keyRepeat = true;
        } else keyRepeat = false;
        if (scrollUpKey.get().isPressed()) {
            if (!upIsPressed) {
                if (currentLine > maxLines.get())
                    currentLine -= maxLines.get();
                else currentLine = 0;
                scrollTick = 0;
            }
            upIsPressed = true;
        } else upIsPressed = false;
        if (scrollDownKey.get().isPressed()) {
            if (!downIsPressed) {
                if (currentLine + 2 * maxLines.get() < lines.size())
                    currentLine += maxLines.get();
                else
                    currentLine = lines.size() - maxLines.get();
                scrollTick = 0;
            }
            downIsPressed = true;
        } else downIsPressed = false;
        if (activateKey.get().isSet() && !activateKey.get().isPressed())
            return;
        if (showDelimiters.get())
            event.appendEnd(Component.literal("-- nbt start --").withColor(TextColor.DARK_PURPLE));
        for (int i = currentLine; i < Math.min(currentLine + maxLines.get(), lines.size()); ++i) {
            event.appendEnd(lines.get(i));
        }
        if (showDelimiters.get())
            event.appendEnd(Component.literal("-- nbt end --").withColor(TextColor.DARK_PURPLE));
        int tt = fastScrollKey.get().isPressed() ? fastScrollDelay.get() : normalScrollDelay.get();
        if (scrollTick++ > tt) {
            ++currentLine;
            if (currentLine + maxLines.get() > lines.size()) {
                currentLine = 0;
            }
            scrollTick = 0;
        }
    }
}
