// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.screens.settings.ItemSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WItemWithLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.ItemSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;

public class AcceptablePricesScreen extends WindowScreen {
    private final AcceptablePrices targetValue;
    private WTable table;
    private ArrayList<Item> rowItems;
    public AcceptablePricesScreen(GuiTheme theme, AcceptablePrices prices) {
        super(theme, "Configure max prices for each trade");
        targetValue = prices;
        rowItems = new ArrayList<>();
    }

    @Override
    public void initWidgets() {
        this.add(theme.label("The amount set here corresponds to the max number of items in the first input slot."));
        table = this.add(theme.table()).expandX().widget();
        initTable();
        WHorizontalList hl = theme.horizontalList();
        hl.add(theme.label("")).expandCellX();
        hl.add(theme.plus()).widget().action = () -> {
            ItemSetting t = new ItemSetting("", "", Items.AIR, null, null, null,
                (Item item) -> AcceptablePrices.allItems.contains(item) && (targetValue.getMaxPriceForItem(item) == null));
            ItemSettingScreen screen = new ItemSettingScreen(this.theme, t);
            screen.onClosed(() -> {
                Item item = t.get();
                if (item != null && item != Items.AIR)
                    addRowForItem(item);
            });
            Minecraft.getInstance().gui.setScreen(screen);
        };
        this.add(hl).expandX();
    }

    private void initTable() {
        for (Item i : targetValue.allConfiguredItems()) {
            addRowForItem(i);
        }
    }
    private void addRowForItem(Item item) {
        rowItems.add(item);
        WItemWithLabel itemDisplay = theme.itemWithLabel(item.getDefaultInstance(), item.getName(item.getDefaultInstance()).getString());
        table.add(itemDisplay).expandCellX();
        Integer v = targetValue.getMaxPriceForItem(item);
        if (v == null) {
            targetValue.setMaxPriceForItem(item, 1);
            v = 1;
        }
        WIntEdit priceEdit = theme.intEdit(v, 1, 64, true);
        priceEdit.action = () -> {
            targetValue.setMaxPriceForItem(item, priceEdit.get());
        };
        table.add(priceEdit);
        WMinus deleteButton = theme.minus();
        deleteButton.action = () -> {
            table.removeRow(rowItems.indexOf(item));
            this.invalidate();
            rowItems.remove(item);
            targetValue.unsetMaxPriceForItem(item);
        };
        table.add(deleteButton);
        table.row();
    }
}
