/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.economy.AtomicJson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final File TRADES_FILE = CONFIG_DIR.resolve("shaicraft-economy-trades.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Map<PlayerUUID, Map<CoinID, Holding>>
    private Map<UUID, Map<String, Holding>> cryptoHoldings = new HashMap<>();
    private boolean dirty;

    public static class Holding {
        public double amount;
        public double totalCost;

        public Holding(double amount, double price) {
            this.amount = amount;
            this.totalCost = amount * price;
        }

        public void add(double amount, double price) {
            this.amount += amount;
            this.totalCost += amount * price;
        }

        public void remove(double amount) {
            if (this.amount <= 0) return;
            double avg = getAveragePrice();
            this.amount -= amount;
            this.totalCost -= amount * avg;
            if (this.amount < 0) { this.amount = 0; this.totalCost = 0; }
        }

        public double getAveragePrice() {
            return amount > 0 ? totalCost / amount : 0.0;
        }
    }

    public void load() {
        if (!TRADES_FILE.exists()) return;
        try (FileReader reader = new FileReader(TRADES_FILE)) {
            Map<UUID, Map<String, Holding>> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, Map<String, Holding>>>() {}.getType());
            if (loaded != null) cryptoHoldings.putAll(loaded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean saveIfDirty() {
        if (!dirty) return false;
        try {
            AtomicJson.write(TRADES_FILE, GSON.toJson(cryptoHoldings));
            dirty = false;
            return true;
        } catch (IOException e) {
            ShaiCraftEconomy.LOGGER.error("Sauvegarde des cryptos impossible.", e);
            return false;
        }
    }

    public void recordBuy(UUID uuid, String coinId, double price, double amount) {
        cryptoHoldings.computeIfAbsent(uuid, k -> new HashMap<>())
                      .computeIfAbsent(coinId, k -> new Holding(0, 0))
                      .add(amount, price);
        dirty = true;
    }

    public boolean recordSell(UUID uuid, String coinId, double price, double amount) {
        Map<String, Holding> holdings = cryptoHoldings.get(uuid);
        if (holdings == null || !holdings.containsKey(coinId)) return false;

        Holding h = holdings.get(coinId);
        if (h.amount + 1.0e-8 < amount) return false;

        h.remove(amount);
        dirty = true;
        return true;
    }

    public double getAmount(UUID uuid, String coinId) {
        Map<String, Holding> holdings = cryptoHoldings.get(uuid);
        return holdings != null && holdings.containsKey(coinId) ? holdings.get(coinId).amount : 0.0;
    }

    public double getAveragePrice(UUID uuid, String coinId) {
        Map<String, Holding> holdings = cryptoHoldings.get(uuid);
        return holdings != null && holdings.containsKey(coinId) ? holdings.get(coinId).getAveragePrice() : 0.0;
    }

    public Map<String, Double> getAverages(UUID uuid) {
        Map<String, Double> avgs = new HashMap<>();
        Map<String, Holding> holdings = cryptoHoldings.get(uuid);
        if (holdings != null) {
            holdings.forEach((k, v) -> avgs.put(k, v.getAveragePrice()));
        }
        return avgs;
    }
}
