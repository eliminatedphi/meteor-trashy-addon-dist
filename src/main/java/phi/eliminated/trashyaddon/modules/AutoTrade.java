// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.EntityHitResult;

import java.util.*;

public class AutoTrade extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public static final List<Item> allSellItems = List.of(
        Items.COAL,
        Items.CHICKEN,
        Items.PORKCHOP,
        Items.RABBIT,
        Items.MUTTON,
        Items.BEEF,
        Items.DRIED_KELP_BLOCK,
        Items.SWEET_BERRIES,
        Items.IRON_INGOT,
        Items.DIAMOND,
        Items.PAPER,
        Items.GLASS_PANE,
        Items.ROTTEN_FLESH,
        Items.GOLD_INGOT,
        Items.RABBIT_FOOT,
        Items.TURTLE_SCUTE,
        Items.GLASS_BOTTLE,
        Items.NETHER_WART,
        Items.WHEAT,
        Items.POTATO,
        Items.CARROT,
        Items.BEETROOT,
        Items.PUMPKIN,
        Items.MELON,
        Items.STRING,
        Items.COD,
        Items.SALMON,
        Items.TROPICAL_FISH,
        Items.PUFFERFISH,
        Items.STICK,
        Items.FLINT,
        Items.FEATHER,
        Items.TRIPWIRE_HOOK,
        Items.LEATHER,
        Items.RABBIT_HIDE,
        Items.BOOK,
        Items.INK_SAC,
        Items.CLAY_BALL,
        Items.STONE,
        Items.GRANITE,
        Items.ANDESITE,
        Items.DIORITE,
        Items.QUARTZ
    );

    public static final List<Item> allBuyItems = List.of(
        Items.BELL,
        Items.COOKED_PORKCHOP,
        Items.COOKED_CHICKEN,
        Items.MAP,
        Items.ITEM_FRAME,
        Items.REDSTONE,
        Items.LAPIS_LAZULI,
        Items.GLOWSTONE,
        Items.ENDER_PEARL,
        Items.EXPERIENCE_BOTTLE,
        Items.BREAD,
        Items.PUMPKIN_PIE,
        Items.APPLE,
        Items.COOKIE,
        Items.GOLDEN_CARROT,
        Items.GLISTERING_MELON_SLICE,
        Items.CAMPFIRE,
        Items.ARROW,
        Items.BOOKSHELF,
        Items.LANTERN,
        Items.GLASS,
        Items.CLOCK,
        Items.COMPASS,
        Items.NAME_TAG,
        Items.BRICK,
        Items.CHISELED_STONE_BRICKS,
        Items.DRIPSTONE_BLOCK,
        Items.POLISHED_ANDESITE,
        Items.POLISHED_DIORITE,
        Items.POLISHED_GRANITE,
        Items.QUARTZ_PILLAR,
        Items.QUARTZ_BLOCK,
        Items.PAINTING
    );

    private final Setting<Boolean> sellingEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("Enable Selling")
        .build()
    );
    private final Setting<List<Item>> sellsSetting = sgGeneral.add(new ItemListSetting.Builder()
        .name("Sells")
        .description("Items to automatically SELL TO villagers.")
        .filter((item) -> allSellItems.contains(item))
        .build()
    );
    private final Setting<Boolean> buyingEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("Enable Buying")
        .build()
    );
    private final Setting<List<Item>> buysSetting = sgGeneral.add(new ItemListSetting.Builder()
        .name("Buys")
        .description("Items to automatically BUY FROM villagers.")
        .filter((item) -> allBuyItems.contains(item))
        .build()
    );
    private final Setting<AcceptablePrices> acceptablePricesSetting = sgGeneral.add(new GenericSetting.Builder<AcceptablePrices>()
        .name("acceptable-prices")
        .description("Configure maximum acceptable price for each item.")
        .defaultValue(new AcceptablePrices(new HashMap<>()))
        .build()
    );
    private final Setting<Integer> interactionRate = sgGeneral.add(new IntSetting.Builder()
        .name("Interaction Rate")
        .description("Number of ticks between interactions.")
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Close")
        .description("Close trading screen after any successful trades, or if trading was initiated automatically.")
        .build()
    );
    private final Setting<Double> autoInitiateDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("Auto Initiate Distance")
        .description("Starts trading automatically if a villager is selected and distance in between is less than this value. 0 = Disable.")
        .min(0)
        .max(10)
        .defaultValue(5)
        .build()
    );
    private final Setting<Boolean> logSummary = sgGeneral.add(new BoolSetting.Builder()
        .name("Log Summary")
        .description("Give a summary of what has been traded once trading is complete.")
        .build()
    );

    private MerchantOffers offers;
    private MerchantScreen screen;
    private int currentOffer;
    private int ticksRemaining;
    private int countBefore;
    private int countAfter;
    private UUID lastAutoInteractedVillager;

    private enum WaitState {
        None,
        WaitingForOutput,
        WaitingForInventoryUpdate,
        WaitingForFinalize
    };
    private WaitState waitState;
    private ArrayList<ItemStack> itemsSold;
    private ArrayList<ItemStack> itemsBought;


    public AutoTrade() {
        super(Categories.World, "auto-trade", "Help prevent arthritis from excessive trading with villagers.");
        itemsSold = new ArrayList<>();
        itemsBought = new ArrayList<>();
        waitState = WaitState.None;
        lastAutoInteractedVillager = null;
    }

    /*
    @Override
    public String getInfoString() {
        return waitState.toString() + " " + ticksRemaining;
    }
     */

    private boolean isOfferEligible(MerchantOffer o) {
        // I don't give a SHIT to trades that use the second slot
        // well actually I do, but I don't need THAT many enchanted books...
        if (!o.getCostB().isEmpty())
            return false;
        if (o.isOutOfStock())
            return false;
        ItemStack mbuy = o.getCostA();
        ItemStack msell = o.getResult();
        List<Item> sells = sellingEnabled.get() ? this.sellsSetting.get() : List.of();
        List<Item> buys = buyingEnabled.get() ? this.buysSetting.get() : List.of();
        Item interestedItem = null;
        if (sells.contains(mbuy.getItem()))
            interestedItem = mbuy.getItem();
        if (buys.contains(msell.getItem()))
            interestedItem = msell.getItem();
        if (interestedItem == null)
            return false;
        Integer maxPrice = acceptablePricesSetting.get().getMaxPriceForItem(interestedItem);
        if (maxPrice != null && mbuy.getCount() > maxPrice) {
            return false;
        }
        FindItemResult rs = InvUtils.find((stack) -> stack.getItem().equals(mbuy.getItem()) && stack.getCount() >= mbuy.getCount());
        ItemStack s0is = screen.getMenu().slots.get(0).getItem();
        boolean remainingFirstSlotSufficient = s0is.getItem().equals(mbuy.getItem()) && s0is.getCount() >= mbuy.getCount();
        if (!rs.found() && !remainingFirstSlotSufficient)
            return false;
        return true;
    }

    private void selectTrade() {
        while (currentOffer < offers.size() && !isOfferEligible(offers.get(currentOffer)))
            ++currentOffer;
        if (currentOffer >= offers.size()) {
            ticksRemaining = interactionRate.get();
            waitState = WaitState.WaitingForFinalize;
            return;
        }
        waitState = WaitState.WaitingForOutput;
        ticksRemaining = -0xdead;
        MerchantOffer o = offers.get(currentOffer);
        Item tradedItem = o.getResult().getItem().equals(Items.EMERALD) ?
            o.getCostA().getItem() : o.getResult().getItem();
        countBefore = screen.getMenu().slots.stream()
            .map(slot -> slot.getItem())
            .filter(s -> s.getItem().equals(tradedItem))
            .map(s -> s.getCount())
            .reduce(0, Integer::sum);
        screen.shopItem = currentOffer;
        screen.postButtonClick();
    }

    private void finalizeCurrentTrade() {
        MerchantOffer o = offers.get(currentOffer);
        Item tradedItem = o.getResult().getItem().equals(Items.EMERALD) ?
            o.getCostA().getItem() : o.getResult().getItem();
        countAfter = screen.getMenu().slots.stream()
            .map(slot -> slot.getItem())
            .filter(s -> s.getItem().equals(tradedItem))
            .map(s -> s.getCount())
            .reduce(0, Integer::sum);
        ArrayList<ItemStack> itemsTraded = countAfter < countBefore ? itemsSold : itemsBought;
        int count = java.lang.Math.abs(countAfter - countBefore);
        Optional<ItemStack> t = itemsTraded.stream().filter(s -> s.getItem().equals(tradedItem)).findFirst();
        if (t.isPresent()) {
            t.get().setCount(t.get().getCount() + count);
        }
        else {
            ItemStack s = new ItemStack(tradedItem, count);
            itemsTraded.add(s);
        }
    }

    private void endTrading() {
        if (logSummary.get()) {
            if (itemsSold.stream().anyMatch(i -> !i.isEmpty())) {
                Formatter f = new Formatter();
                f.format("Item(s) sold:");
                boolean first = true;
                for (ItemStack i : itemsSold) {
                    if (i.isEmpty()) continue;
                    f.format("%s%s*%d", first ? " " : ", ", i.getDisplayName().getString(), i.getCount());
                    first = false;
                }
                info(f.toString());
                f.close();
            }
            if (itemsBought.stream().anyMatch(i -> !i.isEmpty())) {
                Formatter f = new Formatter();
                f.format("Item(s) bought:");
                boolean first = true;
                for (ItemStack i : itemsBought) {
                    if (i.isEmpty()) continue;
                    f.format("%s%s*%d", first ? " " : ", ", i.getDisplayName().getString(), i.getCount());
                    first = false;
                }
                info(f.toString());
                f.close();
            }
        }
        if (autoClose.get() && (!itemsBought.isEmpty() || !itemsSold.isEmpty() || lastAutoInteractedVillager != null))
            screen.onClose();
        screen = null;
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (ticksRemaining > 0) {
            --ticksRemaining;
            return;
        }
        if (offers != null && screen != null) {
            if (waitState == WaitState.WaitingForFinalize) {
                endTrading();
                return;
            }
            if (waitState != WaitState.None && currentOffer < offers.size() && (offers.get(currentOffer).isOutOfStock() ||
                screen.getMenu().slots.get(0).getItem().getCount() < offers.get(currentOffer).getCostA().getCount())) {
                // the WaitingForInventoryUpdate state is mostly useless because ScreenHandlerSlotUpdateS2CPacket
                // isn't sent after the shift-click...
                finalizeCurrentTrade();
                waitState = WaitState.None;
                ticksRemaining = interactionRate.get();
                return;
            }
            if (ticksRemaining == -0xdead)
                return;
            switch (waitState) {
                case None: selectTrade(); break;
                case WaitingForOutput:
                    InvUtils.shiftClick().slotId(2);
                    waitState = WaitState.WaitingForInventoryUpdate;
                    ticksRemaining = -0xdead;
                break;
                case WaitingForInventoryUpdate:
                    finalizeCurrentTrade();
                    waitState = WaitState.None;
                    ticksRemaining = interactionRate.get();
                break;
            }
            return;
        }
        if (mc.hitResult instanceof EntityHitResult ehr) {
            if (ehr.getEntity() instanceof Villager v) {
                Holder<VillagerProfession> p = v.getVillagerData().profession();
                if (!(p.is(VillagerProfession.NONE) || p.is(VillagerProfession.NITWIT))) {
                    if (ehr.distanceTo(mc.player) < autoInitiateDistance.get()) {
                        if (lastAutoInteractedVillager == null || !v.getUUID().equals(lastAutoInteractedVillager)) {
                            lastAutoInteractedVillager = v.getUUID();
                            mc.gameMode.interact(mc.player, v, ehr, InteractionHand.MAIN_HAND);
                        }
                    }
                } else {
                    lastAutoInteractedVillager = null;
                }
            } else {
                lastAutoInteractedVillager = null;
            }
        } else {
            lastAutoInteractedVillager = null;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive e) {
        if (e.packet instanceof ClientboundMerchantOffersPacket p) {
            this.offers = p.getOffers();
            currentOffer = 0;
            waitState = WaitState.None;
            ticksRemaining = 0;
            itemsSold.clear();
            itemsBought.clear();
        } else if (e.packet instanceof ClientboundContainerSetSlotPacket p) {
            if (screen == null || p.getContainerId() != screen.getMenu().containerId || p.getSlot() != 2)
                return;
            if (waitState == WaitState.WaitingForOutput) {
                ticksRemaining = interactionRate.get();
            }
            if (waitState == WaitState.WaitingForInventoryUpdate) {
                if (!p.getItem().isEmpty())
                    waitState = WaitState.WaitingForOutput;
                ticksRemaining = interactionRate.get();
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent e) {
        if (e.screen == null) {
            this.screen = null;
            this.offers = null;
        }
        else if (e.screen instanceof MerchantScreen s) {
            this.screen = s;
        }
    }
}
