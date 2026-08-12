/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final File MARKET_FILE = CONFIG_DIR.resolve("shaicraft-economy-market.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, MarketItem> marketData = new HashMap<>();

    public static class MarketItem {
        public double currentPrice;
        public double basePrice;
        public List<Double> history = new ArrayList<>();
        public double lastChangePercent = 0.0;

        public MarketItem(double basePrice) {
            this.basePrice = basePrice;
            this.currentPrice = basePrice;
        }
    }

    public MarketManager() {
        initDefaults();
    }

    private void initDefaults() {
        marketData.put("minecraft:diamond", new MarketItem(100.0));
        marketData.put("minecraft:iron_ingot", new MarketItem(10.0));
        marketData.put("minecraft:gold_ingot", new MarketItem(20.0));
        marketData.put("minecraft:emerald", new MarketItem(50.0));
        marketData.put("minecraft:netherite_ingot", new MarketItem(500.0));
        marketData.put("pikachu", new MarketItem(500.0));
        marketData.put("mewtwo", new MarketItem(5000.0));
    }

    public void load() {
        if (!MARKET_FILE.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(MARKET_FILE)) {
            Map<String, MarketItem> loaded = GSON.fromJson(reader, new TypeToken<Map<String, MarketItem>>() {}.getType());
            if (loaded != null) {
                marketData.putAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(MARKET_FILE)) {
            GSON.toJson(marketData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getPrice(String id) {
        MarketItem item = marketData.get(id);
        return item != null ? item.currentPrice : 0.0;
    }

    public MarketItem getMarketItem(String id) {
        return marketData.get(id);
    }

    public void updatePrice(String id, boolean buy) {
        // PRICES ARE NOW FIXED - DO NOTHING
    }
    
    public Map<String, MarketItem> getAllMarketData() {
        return marketData;
    }
}
