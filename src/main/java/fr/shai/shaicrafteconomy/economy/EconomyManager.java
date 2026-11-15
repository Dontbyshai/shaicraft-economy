/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File dataFile;
    private final File namesFile;
    private Map<UUID, Double> balances = new HashMap<>();
    private Map<UUID, String> names = new HashMap<>();
    private boolean balancesDirty;
    private boolean namesDirty;

    public EconomyManager() {
        File configDir = FabricLoader.getInstance().getConfigDir().toFile();
        this.dataFile = new File(configDir, "shaicraft-economy-balances.json");
        this.namesFile = new File(configDir, "shaicraft-economy-names.json");
    }

    public void load() {
        Map<UUID, Double> loadedBalances = readMap(dataFile, new TypeToken<Map<UUID, Double>>() {}.getType());
        balances = loadedBalances == null ? new HashMap<>() : loadedBalances;
        Map<UUID, String> loadedNames = readMap(namesFile, new TypeToken<Map<UUID, String>>() {}.getType());
        names = loadedNames == null ? new HashMap<>() : loadedNames;
        balancesDirty = false;
        namesDirty = false;
    }

    private <T> T readMap(File file, Type type) {
        if (!file.exists()) return null;
        try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            T loaded = GSON.fromJson(reader, type);
            if (loaded == null) {
                ShaiCraftEconomy.LOGGER.error("Fichier vide ou invalide : {}", file.getName());
                return null;
            }
            return loaded;
        } catch (Exception e) {
            ShaiCraftEconomy.LOGGER.error("Lecture impossible de {}. Les données en mémoire ne sont pas remplacées par un fichier vide.", file.getName(), e);
            return null;
        }
    }

    /** Écrit le fichier seulement s'il a changé depuis la dernière sauvegarde. */
    public boolean saveIfDirty() {
        boolean wrote = false;
        if (balancesDirty && write(dataFile, balances)) {
            balancesDirty = false;
            wrote = true;
        }
        if (namesDirty && write(namesFile, names)) {
            namesDirty = false;
            wrote = true;
        }
        return wrote;
    }

    private boolean write(File file, Object data) {
        try {
            AtomicJson.write(file, GSON.toJson(data));
            return true;
        } catch (Exception e) {
            ShaiCraftEconomy.LOGGER.error("Sauvegarde impossible : {}", file.getName(), e);
            return false;
        }
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void setBalance(UUID uuid, double amount) {
        if (!Double.isFinite(amount)) return;
        if (amount < 0) amount = 0;
        balances.put(uuid, amount);
        balancesDirty = true;
        notifyIfOnline(uuid);
    }

    public void addBalance(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean removeBalance(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        double current = getBalance(uuid);
        if (current + 1e-6 < amount) return false;
        setBalance(uuid, current - amount);
        return true;
    }

    public void rememberName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return;
        if (name.equals(names.get(uuid))) return;
        names.put(uuid, name);
        namesDirty = true;
    }

    public String getName(UUID uuid) {
        String known = names.get(uuid);
        if (known != null && !known.isBlank()) return known;
        return uuid.toString().substring(0, 8);
    }

    public Map<UUID, Double> getAllBalances() {
        return new HashMap<>(balances);
    }

    /** Met à jour l'écran du joueur s'il est connecté. */
    private void notifyIfOnline(UUID uuid) {
        var server = fr.shai.shaicrafteconomy.ShaiCraftEconomy.getInstance().getServer();
        if (server == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            fr.shai.shaicrafteconomy.network.Networking.sendBalanceUpdate(player, getBalance(uuid));
        }
    }
}
