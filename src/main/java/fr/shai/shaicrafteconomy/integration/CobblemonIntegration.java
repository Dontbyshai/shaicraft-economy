/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.integration;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

public class CobblemonIntegration {

    private static final Random RNG = new Random();

    public static int sellPokemon(ServerPlayerEntity player, int slot) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon pokemon = party.get(slot);

        if (pokemon == null) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.no_pokemon_in_slot", slot + 1).formatted(Formatting.RED));
            return 0;
        }

        double price = calculatePrice(pokemon);
        party.remove(pokemon);
        ShaiCraftEconomy.getInstance().getEconomyManager().addBalance(player.getUuid(), price);
        ShaiCraftEconomy.getInstance().getTransactionLog().add(player.getUuid(), "SHOP_SELL", price, pokemon.getSpecies().getName());
        player.sendMessage(Text.translatable("msg.shaicraft-economy.pokemon_sell_success",
            pokemon.getDisplayName(true).getString(),
            String.format("%.2f", price)).formatted(Formatting.GREEN));
        return 1;
    }

    /**
     * Buys a Pokémon from the shop and gives it a random level appropriate
     * to its price tier (simulating what you'd find in the wild).
     *
     * Price tiers → wild-like level ranges:
     *  < 10,000 (common)     :  5 – 20
     *  < 30,000 (uncommon)   : 15 – 35
     *  < 80,000 (rare)       : 25 – 50
     *  < 200,000 (pseudo-leg): 35 – 60
     *  >= 200,000 (legendary) : 50 – 70
     */
    public static int buyPokemon(ServerPlayerEntity player, String species, double price) {
        if (!ShaiCraftEconomy.getInstance().getEconomyManager().removeBalance(player.getUuid(), price)) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
            return 0;
        }

        int level = randomLevelForPrice(price);
        Pokemon pokemon = PokemonProperties.Companion.parse("species=" + species + " level=" + level).create();

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        boolean inParty = party.add(pokemon);
        if (!inParty) {
            Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
        }

        ShaiCraftEconomy.getInstance().getTransactionLog().add(player.getUuid(), "SHOP_BUY", -price, species);
        player.sendMessage(Text.translatable("msg.shaicraft-economy.pokemon_bought",
            pokemon.getDisplayName(true).getString(), level, String.format("%,.0f", price)).formatted(Formatting.AQUA));
        player.sendMessage(Text.translatable(inParty
            ? "msg.shaicraft-economy.pokemon_to_party"
            : "msg.shaicraft-economy.pokemon_to_pc").formatted(inParty ? Formatting.GREEN : Formatting.YELLOW));
        return 1;
    }

    private static int randomLevelForPrice(double price) {
        if (price >= 200_000) {
            // Legendary: 50–70
            return 50 + RNG.nextInt(21);
        } else if (price >= 80_000) {
            // Pseudo-legendary: 35–60
            return 35 + RNG.nextInt(26);
        } else if (price >= 30_000) {
            // Rare: 25–50
            return 25 + RNG.nextInt(26);
        } else if (price >= 10_000) {
            // Uncommon (starters, special): 15–35
            return 15 + RNG.nextInt(21);
        } else {
            // Common: 5–20
            return 5 + RNG.nextInt(16);
        }
    }

    public static double calculatePrice(Pokemon pokemon) {
        double base = 100.0;
        base += pokemon.getLevel() * 2.0;
        if (pokemon.getShiny()) base += 500.0;
        if (pokemon.isLegendary()) base += 2000.0;
        return base;
    }
}
