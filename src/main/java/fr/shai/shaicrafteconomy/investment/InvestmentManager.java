/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.investment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InvestmentManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final File INVESTMENT_FILE = CONFIG_DIR.resolve("shaicraft-economy-investments.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Map<PlayerUUID, Map<InvestmentID, Level>>
    private Map<UUID, Map<String, Integer>> playerInvestments = new HashMap<>();

    public void load() {
        if (!INVESTMENT_FILE.exists()) return;
        try (FileReader reader = new FileReader(INVESTMENT_FILE)) {
            Map<UUID, Map<String, Integer>> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, Map<String, Integer>>>() {}.getType());
            if (loaded != null) playerInvestments.putAll(loaded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(INVESTMENT_FILE)) {
            GSON.toJson(playerInvestments, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getLevel(UUID uuid, String id) {
        Map<String, Integer> investments = playerInvestments.get(uuid);
        return investments != null ? investments.getOrDefault(id, 0) : 0;
    }

    public void setLevel(UUID uuid, String id, int level) {
        playerInvestments.computeIfAbsent(uuid, k -> new HashMap<>()).put(id, level);
        save();
    }

    public Map<String, Integer> getInvestments(UUID uuid) {
        return playerInvestments.getOrDefault(uuid, new HashMap<>());
    }

    public double calculateTotalYield(UUID uuid) {
        Map<String, Integer> investments = playerInvestments.get(uuid);
        if (investments == null) return 0.0;

        double total = 0;
        for (InvestmentType type : InvestmentType.values()) {
            int level = investments.getOrDefault(type.getId(), 0);
            total += type.getYield(level);
        }
        return total;
    }
}
