// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.settings.IGeneric;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

public class AcceptablePrices implements ICopyable<AcceptablePrices>, ISerializable<AcceptablePrices>, IGeneric<AcceptablePrices> {
    public static final List<Item> allItems = Stream.concat(AutoTrade.allSellItems.stream(), AutoTrade.allBuyItems.stream()).toList();
    private HashMap<Item, Integer> prices;
    AcceptablePrices(HashMap<Item, Integer> prices) {
        this.prices = prices;
    }
    Integer getMaxPriceForItem(Item i) {
        return prices.get(i);
    }
    void setMaxPriceForItem(Item i, int p) {
        prices.put(i, p);
    }
    void unsetMaxPriceForItem(Item i) {
        prices.remove(i);
    }
    List<Item> allConfiguredItems() {
        return prices.keySet().stream().toList();
    }

    @Override
    public WidgetScreen createScreen(GuiTheme theme, GenericSetting<AcceptablePrices> unused) {
        return new AcceptablePricesScreen(theme, this);
    }

    @Override
    public AcceptablePrices set(AcceptablePrices value) {
        this.prices = value.prices;
        return this;
    }

    @Override
    public AcceptablePrices copy() {
        return new AcceptablePrices(new HashMap<>(this.prices));
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag ret = new CompoundTag();
        ListTag l = new ListTag();
        for (Item i : this.prices.keySet()) {
            CompoundTag a = new CompoundTag();
            a.putString("Item", BuiltInRegistries.ITEM.getKey(i).toString());
            a.putInt("Price", prices.get(i));
            l.add(a);
        }
        ret.put("Prices", l);
        return ret;
    }

    @Override
    public AcceptablePrices fromTag(CompoundTag tag) {
        HashMap<Item, Integer> ret = new HashMap<>();
        try {
            ListTag l = tag.getList("Prices").orElseThrow();
            for (int i = 0; i < l.size(); ++i) {
                CompoundTag a = l.getCompound(i).orElseThrow();
                String item_id = a.getString("Item").orElseThrow();
                Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(Identifier.parse(item_id));
                if (item.isEmpty()) continue;
                int price = a.getInt("Price").orElseThrow();
                ret.put(item.get().value(), price);
            }
        } catch (NullPointerException | NoSuchElementException e) {
            this.prices = new HashMap<>();
            return this;
        }
        this.prices = ret;
        return this;
    }
}
