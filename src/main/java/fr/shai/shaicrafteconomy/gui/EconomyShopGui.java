/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.gui;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.economy.EconomyManager;
import fr.shai.shaicrafteconomy.network.Networking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import java.util.HashMap;
import java.util.Map;

public class EconomyShopGui implements NamedScreenHandlerFactory {
    private static final Map<Item, Double> BUY_PRICES = new HashMap<>();
    private static final Map<Item, Double> SELL_PRICES = new HashMap<>();
    private static final Map<Integer, PokemonEntry> POKEMON_SHOP = new HashMap<>();

    private record PokemonEntry(String species, double price, String displayName, Formatting color) {}

    static {
        // --- SELLING PRICES (Player sells to shop) ---
        SELL_PRICES.put(Items.GOLD_INGOT, 50.0);
        SELL_PRICES.put(Items.DIAMOND, 200.0);
        SELL_PRICES.put(Items.EMERALD, 150.0);
        SELL_PRICES.put(Items.IRON_INGOT, 10.0);
        SELL_PRICES.put(Items.GOLD_BLOCK, 450.0);

        // --- BUYING PRICES (Player buys from shop) ---
        // Basic Materials
        BUY_PRICES.put(Items.DIAMOND, 300.0);
        BUY_PRICES.put(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 5000.0);

        // Cobblemon Items
        try {
            registerBuyItem("cobblemon:poke_ball", 50.0);
            registerBuyItem("cobblemon:great_ball", 150.0);
            registerBuyItem("cobblemon:ultra_ball", 400.0);
            registerBuyItem("cobblemon:master_ball", 25000.0);
            
            registerBuyItem("cobblemon:fire_stone", 5000.0);
            registerBuyItem("cobblemon:water_stone", 5000.0);
            registerBuyItem("cobblemon:thunder_stone", 5000.0);
            registerBuyItem("cobblemon:leaf_stone", 5000.0);
            registerBuyItem("cobblemon:moon_stone", 5000.0);
            registerBuyItem("cobblemon:sun_stone", 5000.0);
            
            registerBuyItem("cobblemon:rare_candy", 2000.0);
            registerBuyItem("cobblemon:exp_share", 15000.0);
        } catch (Exception e) {}

        // --- POKEMON SHOP (Slots 27-53) ---
        // Starters (10,000e)
        POKEMON_SHOP.put(27, new PokemonEntry("bulbasaur", 10000.0, "Bulbasaur", Formatting.GREEN));
        POKEMON_SHOP.put(28, new PokemonEntry("charmander", 10000.0, "Charmander", Formatting.RED));
        POKEMON_SHOP.put(29, new PokemonEntry("squirtle", 10000.0, "Squirtle", Formatting.BLUE));
        POKEMON_SHOP.put(30, new PokemonEntry("pikachu", 10000.0, "Pikachu", Formatting.YELLOW));
        POKEMON_SHOP.put(31, new PokemonEntry("eevee", 10000.0, "Eevee", Formatting.WHITE));

        // Rares (25,000e)
        POKEMON_SHOP.put(36, new PokemonEntry("dratini", 25000.0, "Dratini", Formatting.AQUA));
        POKEMON_SHOP.put(37, new PokemonEntry("ralts", 25000.0, "Ralts", Formatting.LIGHT_PURPLE));
        POKEMON_SHOP.put(38, new PokemonEntry("bagon", 25000.0, "Bagon", Formatting.BLUE));
        POKEMON_SHOP.put(39, new PokemonEntry("beldum", 25000.0, "Beldum", Formatting.GRAY));

        // Legendaries (250,000e)
        POKEMON_SHOP.put(45, new PokemonEntry("mewtwo", 500000.0, "Mewtwo", Formatting.DARK_PURPLE));
        POKEMON_SHOP.put(46, new PokemonEntry("mew", 300000.0, "Mew", Formatting.LIGHT_PURPLE));
        POKEMON_SHOP.put(47, new PokemonEntry("celebi", 300000.0, "Celebi", Formatting.GREEN));
        POKEMON_SHOP.put(48, new PokemonEntry("rayquaza", 750000.0, "Rayquaza", Formatting.DARK_GREEN));
        POKEMON_SHOP.put(49, new PokemonEntry("arceus", 1000000.0, "Arceus", Formatting.WHITE));

        // More Starters (10,000e)
        POKEMON_SHOP.put(32, new PokemonEntry("chikorita", 10000.0, "Chikorita", Formatting.GREEN));
        POKEMON_SHOP.put(33, new PokemonEntry("cyndaquil", 10000.0, "Cyndaquil", Formatting.RED));
        POKEMON_SHOP.put(34, new PokemonEntry("totodile", 10000.0, "Totodile", Formatting.BLUE));
        POKEMON_SHOP.put(35, new PokemonEntry("treecko", 12000.0, "Treecko", Formatting.GREEN));
        
        // More Rares (25,000e - 50,000e)
        POKEMON_SHOP.put(40, new PokemonEntry("gible", 40000.0, "Gible", Formatting.BLUE));
        POKEMON_SHOP.put(41, new PokemonEntry("larvitar", 40000.0, "Larvitar", Formatting.GREEN));
        POKEMON_SHOP.put(42, new PokemonEntry("deino", 40000.0, "Deino", Formatting.DARK_GRAY));
        POKEMON_SHOP.put(43, new PokemonEntry("goomy", 40000.0, "Goomy", Formatting.LIGHT_PURPLE));
        POKEMON_SHOP.put(44, new PokemonEntry("dreepy", 45000.0, "Dreepy", Formatting.DARK_AQUA));

        // More Stones
        try {
            registerBuyItem("cobblemon:shiny_stone", 7500.0);
            registerBuyItem("cobblemon:dusk_stone", 7500.0);
            registerBuyItem("cobblemon:dawn_stone", 7500.0);
            registerBuyItem("cobblemon:ice_stone", 7500.0);
        } catch (Exception e) {}
    }

    private static void registerBuyItem(String id, double price) {
        Item item = Registries.ITEM.get(Identifier.of(id));
        if (item != Items.AIR) {
            BUY_PRICES.put(item, price);
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("gui.shaicraft-economy.shop_title").formatted(Formatting.BOLD, Formatting.GOLD);
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(54);
        
        // Fill Item shop
        int slot = 0;
        for (Map.Entry<Item, Double> entry : BUY_PRICES.entrySet()) {
            if (slot >= 27) break; 
            ItemStack stack = new ItemStack(entry.getKey());
            stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("gui.shaicraft-economy.buy_price", String.format("%.2f", entry.getValue())).formatted(Formatting.YELLOW),
                Text.empty(),
                Text.translatable("gui.shaicraft-economy.click_buy").formatted(Formatting.AQUA)
            )));
            inventory.setStack(slot++, stack);
        }

        // Fill Sellable items (middle row)
        slot = 18;
        for (Map.Entry<Item, Double> entry : SELL_PRICES.entrySet()) {
            if (slot >= 27) break;
            ItemStack stack = new ItemStack(entry.getKey());
            stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("gui.shaicraft-economy.sell_price", String.format("%.2f", entry.getValue())).formatted(Formatting.GREEN),
                Text.empty(),
                Text.translatable("gui.shaicraft-economy.click_sell").formatted(Formatting.GOLD)
            )));
            inventory.setStack(slot++, stack);
        }

        // Fill Pokemon Shop
        POKEMON_SHOP.forEach((s, entry) -> {
            ItemStack egg = new ItemStack(Items.EGG);
            egg.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("gui.shaicraft-economy.buy_pokemon", entry.displayName).formatted(entry.color, Formatting.BOLD));
            egg.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("gui.shaicraft-economy.pokemon_price", String.format("%.2f", entry.price)).formatted(Formatting.GOLD),
                Text.empty(),
                Text.translatable("gui.shaicraft-economy.pokemon_dest").formatted(Formatting.DARK_AQUA)
            )));
            inventory.setStack(s, egg);
        });

        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6) {
            @Override
            public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
                if (slotIndex < 0 || slotIndex >= 54) {
                    super.onSlotClick(slotIndex, button, actionType, player);
                    return;
                }

                ItemStack stack = inventory.getStack(slotIndex);
                if (stack.isEmpty()) return;

                EconomyManager eco = ShaiCraftEconomy.getInstance().getEconomyManager();
                ServerPlayerEntity sPlayer = (ServerPlayerEntity) player;

                // Handle Pokemon
                if (POKEMON_SHOP.containsKey(slotIndex)) {
                    PokemonEntry entry = POKEMON_SHOP.get(slotIndex);
                    fr.shai.shaicrafteconomy.integration.CobblemonIntegration.buyPokemon(sPlayer, entry.species, entry.price);
                    ShaiCraftEconomy.getInstance().syncBalance(sPlayer);
                    return;
                }

                Item item = stack.getItem();
                
                // button == 0 -> Left Click (BUY)
                if (button == 0) {
                    Double price = BUY_PRICES.get(item);
                    if (price != null) {
                        if (eco.removeBalance(player.getUuid(), price)) {
                            if (!player.getInventory().insertStack(new ItemStack(item))) {
                                eco.addBalance(player.getUuid(), price);
                                player.sendMessage(Text.translatable("msg.shaicraft-economy.inventory_full").formatted(Formatting.RED));
                                return;
                            }
                            ShaiCraftEconomy.getInstance().getTransactionLog().add(player.getUuid(), "SHOP_BUY", -price, Registries.ITEM.getId(item).toString());
                            player.sendMessage(Text.translatable("msg.shaicraft-economy.buy_success", item.getName().getString(), String.format("%.2f", price)).formatted(Formatting.GREEN));
                            ShaiCraftEconomy.getInstance().syncBalance(sPlayer);
                        } else {
                            player.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
                        }
                    }
                } 
                // button == 1 -> Right Click (SELL)
                else if (button == 1) {
                    Double price = SELL_PRICES.get(item);
                    if (price != null) {
                        // Check if player has the item in inventory
                        int totalCount = 0;
                        for (int i = 0; i < player.getInventory().size(); i++) {
                            ItemStack invStack = player.getInventory().getStack(i);
                            if (invStack.getItem() == item) {
                                totalCount += invStack.getCount();
                            }
                        }

                        if (totalCount > 0) {
                            // Remove 1 item
                            for (int i = 0; i < player.getInventory().size(); i++) {
                                ItemStack invStack = player.getInventory().getStack(i);
                                if (invStack.getItem() == item) {
                                    invStack.decrement(1);
                                    break;
                                }
                            }
                            
                            eco.addBalance(player.getUuid(), price);
                            ShaiCraftEconomy.getInstance().getTransactionLog().add(player.getUuid(), "SHOP_SELL", price, Registries.ITEM.getId(item).toString());
                            player.sendMessage(Text.translatable("msg.shaicraft-economy.sell_success", 1, item.getName().getString(), String.format("%.2f", price)).formatted(Formatting.GOLD));
                            ShaiCraftEconomy.getInstance().syncBalance(sPlayer);
                        } else {
                            player.sendMessage(Text.translatable("msg.shaicraft-economy.missing_item").formatted(Formatting.RED));
                        }
                    }
                }
            }

            @Override
            public boolean canUse(PlayerEntity player) {
                return true;
            }
        };
    }
}
