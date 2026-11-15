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

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Garde les dernières opérations de chaque joueur (paiements, shop, crypto, etc.).
 */
public class TransactionLog {
    public static final int MAX_PER_PLAYER = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        .withZone(ZoneId.systemDefault());

    public static class Entry {
        public long time;
        public String type;
        public double amount;
        public String detail;

        public Entry() {}

        public Entry(long time, String type, double amount, String detail) {
            this.time = time;
            this.type = type;
            this.amount = amount;
            this.detail = detail == null ? "" : detail;
        }
    }

    private final File dataFile;
    private Map<UUID, List<Entry>> entries = new HashMap<>();
    private boolean dirty;

    public TransactionLog() {
        this.dataFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "shaicraft-economy-history.json");
    }

    public void load() {
        entries = new HashMap<>();
        dirty = false;
        if (!dataFile.exists()) return;
        try (Reader reader = new InputStreamReader(Files.newInputStream(dataFile.toPath()), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<UUID, List<Entry>>>() {}.getType();
            Map<UUID, List<Entry>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) entries = loaded;
        } catch (Exception e) {
            ShaiCraftEconomy.LOGGER.error("Historique illisible, il ne sera pas écrasé tant qu'une nouvelle opération n'est pas enregistrée.", e);
        }
    }

    public void add(UUID uuid, String type, double amount, String detail) {
        if (uuid == null || type == null) return;
        List<Entry> list = entries.computeIfAbsent(uuid, key -> new ArrayList<>());
        list.add(new Entry(System.currentTimeMillis(), type, amount, detail));
        while (list.size() > MAX_PER_PLAYER) list.remove(0);
        dirty = true;
    }

    /** De la plus ancienne à la plus récente. */
    public List<Entry> latest(UUID uuid, int limit) {
        List<Entry> list = entries.getOrDefault(uuid, List.of());
        int from = Math.max(0, list.size() - Math.max(0, limit));
        return List.copyOf(list.subList(from, list.size()));
    }

    public boolean saveIfDirty() {
        if (!dirty) return false;
        try {
            AtomicJson.write(dataFile, GSON.toJson(entries));
            dirty = false;
            return true;
        } catch (Exception e) {
            ShaiCraftEconomy.LOGGER.error("Sauvegarde de l'historique impossible.", e);
            return false;
        }
    }

    public static String formatTime(long epochMillis) {
        return TIME.format(Instant.ofEpochMilli(epochMillis));
    }
}
