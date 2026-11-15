/**
 * ShaiCraft Economy - Hôtel des Ventes Pokémon
 */
package fr.shai.shaicrafteconomy.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.economy.AtomicJson;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class PokemonAuctionManager {

    public record AuctionListing(
        String id,           // unique UUID
        String sellerUuid,
        String sellerName,
        String pokemonSpecies,
        int    pokemonLevel,
        String pokemonNbt,   // serialized Cobblemon Pokémon data
        double price,
        long   timestamp
    ) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File dataFile;
    private List<AuctionListing> listings = new ArrayList<>();
    private boolean dirty;

    public PokemonAuctionManager() {
        this.dataFile = new File(
            FabricLoader.getInstance().getConfigDir().toFile(),
            "shaicraft-hdv.json"
        );
    }

    public void load() {
        if (!dataFile.exists()) { listings = new ArrayList<>(); return; }
        try (FileReader r = new FileReader(dataFile)) {
            Type type = new TypeToken<List<AuctionListing>>(){}.getType();
            listings = GSON.fromJson(r, type);
            if (listings == null) listings = new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            listings = new ArrayList<>();
        }
    }

    public boolean saveIfDirty() {
        if (!dirty) return false;
        try {
            AtomicJson.write(dataFile, GSON.toJson(listings));
            dirty = false;
            return true;
        } catch (IOException e) {
            ShaiCraftEconomy.LOGGER.error("Sauvegarde de l'hôtel des ventes impossible.", e);
            return false;
        }
    }

    public void addListing(AuctionListing listing) {
        listings.add(listing);
        dirty = true;
    }

    public boolean removeListing(String id) {
        boolean removed = listings.removeIf(l -> l.id().equals(id));
        if (removed) dirty = true;
        return removed;
    }

    public Optional<AuctionListing> findById(String id) {
        return listings.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    public List<AuctionListing> getListings() {
        return Collections.unmodifiableList(listings);
    }

    public List<AuctionListing> getListingsBySeller(String sellerUuid) {
        return listings.stream().filter(l -> l.sellerUuid().equals(sellerUuid)).toList();
    }
}
