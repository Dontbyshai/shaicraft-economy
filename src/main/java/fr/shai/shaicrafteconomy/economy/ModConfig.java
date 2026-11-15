/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Réglages lus dans config/shaicraft-economy.json.
 * usdToEur : euros pour 1 dollar (0.92 par défaut).
 * payTaxPercent : taxe de /pay en pourcents (2.0 = 2 %).
 * autosaveSeconds : secondes entre deux sauvegardes.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ModConfig current = new ModConfig();

    public double usdToEur = 0.92;
    public double payTaxPercent = 2.0;
    public int autosaveSeconds = 60;

    public static ModConfig get() {
        return current;
    }

    public static void load() {
        File file = new File(FabricLoader.getInstance().getConfigDir().toFile(), "shaicraft-economy.json");
        ModConfig loaded = new ModConfig();
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
                ModConfig parsed = GSON.fromJson(reader, ModConfig.class);
                if (parsed != null) loaded = parsed;
            } catch (Exception e) {
                ShaiCraftEconomy.LOGGER.error("Config illisible, retour aux valeurs sûres.", e);
                loaded = new ModConfig();
            }
        } else {
            try {
                AtomicJson.write(file, GSON.toJson(loaded));
                ShaiCraftEconomy.LOGGER.info("Config créée : {}", file.getAbsolutePath());
            } catch (Exception e) {
                ShaiCraftEconomy.LOGGER.error("Impossible d'écrire la config par défaut.", e);
            }
        }
        loaded.clamp();
        current = loaded;
    }

    private void clamp() {
        if (!Double.isFinite(usdToEur) || usdToEur <= 0.0 || usdToEur > 5.0) usdToEur = 0.92;
        if (!Double.isFinite(payTaxPercent) || payTaxPercent < 0.0) payTaxPercent = 0.0;
        if (payTaxPercent > 50.0) payTaxPercent = 50.0;
        if (autosaveSeconds < 15) autosaveSeconds = 15;
        if (autosaveSeconds > 3600) autosaveSeconds = 3600;
    }
}
