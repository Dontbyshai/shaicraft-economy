/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File dataFile;
    private Map<UUID, Double> balances = new HashMap<>();

    public EconomyManager() {
        this.dataFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "shaicraft-economy-balances.json");
    }

    public void load() {
        if (!dataFile.exists()) {
            balances = new HashMap<>();
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<UUID, Double>>() {}.getType();
            balances = GSON.fromJson(reader, type);
            if (balances == null) balances = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
            balances = new HashMap<>();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            GSON.toJson(balances, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void setBalance(UUID uuid, double amount) {
        balances.put(uuid, amount);
        save();
        
        // Notify player if online
        Object serverObj = net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance();
        if (serverObj instanceof net.minecraft.server.MinecraftServer server) {
            net.minecraft.server.network.ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                fr.shai.shaicrafteconomy.network.Networking.sendBalanceUpdate(player, getBalance(uuid));
            }
        }
    }

    public void addBalance(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean removeBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current < amount) return false;
        setBalance(uuid, current - amount);
        return true;
    }

    public Map<UUID, Double> getAllBalances() {
        return new HashMap<>(balances);
    }
}
