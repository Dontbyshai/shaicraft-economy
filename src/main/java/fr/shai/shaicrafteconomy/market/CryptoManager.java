/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.market;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.shai.shaicrafteconomy.economy.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Prix Binance. Le téléchargement se fait hors du thread Minecraft.
 * Les prix ne sont copiés dans la map utilisée par le jeu que sur le thread serveur.
 */
public class CryptoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("shaicraft-economy");
    private final Map<String, Double> prices = new HashMap<>();
    private final AtomicReference<Map<String, Double>> pending = new AtomicReference<>();
    private boolean fetchFailureLogged;

    public CryptoManager() {
        for (String coin : CryptoTradeRules.COINS) {
            prices.put(coin, CryptoTradeRules.fallbackPrice(coin));
        }
    }

    /**
     * À appeler hors du thread serveur. Ne touche ni aux joueurs, ni aux mondes.
     * @return les prix en euros, ou null si Binance ne répond pas.
     */
    public Map<String, Double> downloadPrices() {
        double usdToEur = ModConfig.get().usdToEur;
        StringBuilder symbols = new StringBuilder("[");
        for (int i = 0; i < CryptoTradeRules.COINS.size(); i++) {
            if (i > 0) symbols.append(',');
            symbols.append('"').append(CryptoTradeRules.binanceSymbol(CryptoTradeRules.COINS.get(i))).append('"');
        }
        symbols.append(']');
        String url = "https://api.binance.com/api/v3/ticker/price?symbols="
            + URLEncoder.encode(symbols.toString(), StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ShaiCraftEconomy/1.1");
            int code = connection.getResponseCode();
            if (code != 200) {
                logFailure("HTTP " + code);
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                List<Map<String, String>> data = new Gson().fromJson(reader, new TypeToken<List<Map<String, String>>>() {}.getType());
                if (data == null || data.isEmpty()) {
                    logFailure("réponse vide");
                    return null;
                }
                Map<String, Double> next = new HashMap<>();
                for (Map<String, String> entry : data) {
                    String coinId = CryptoTradeRules.coinForSymbol(entry.get("symbol"));
                    String raw = entry.get("price");
                    if (coinId == null || raw == null) continue;
                    double priceUsd = Double.parseDouble(raw);
                    if (!Double.isFinite(priceUsd) || priceUsd <= 0) continue;
                    next.put(coinId, priceUsd * usdToEur);
                }
                if (next.isEmpty()) {
                    logFailure("aucun symbole reconnu");
                    return null;
                }
                fetchFailureLogged = false;
                return next;
            }
        } catch (Exception e) {
            logFailure(e.getClass().getSimpleName());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void logFailure(String reason) {
        if (!fetchFailureLogged) {
            LOGGER.warn("Prix Binance indisponibles ({}). Les derniers prix restent en place.", reason);
            fetchFailureLogged = true;
        }
    }

    public void queuePrices(Map<String, Double> fetched) {
        if (fetched != null && !fetched.isEmpty()) {
            pending.set(Map.copyOf(fetched));
        }
    }

    /** À appeler uniquement sur le thread serveur. */
    public synchronized boolean applyPending() {
        Map<String, Double> incoming = pending.getAndSet(null);
        if (incoming == null || incoming.isEmpty()) return false;
        prices.putAll(incoming);
        return true;
    }

    public synchronized double getPrice(String id) {
        Double value = prices.get(id);
        return value == null ? 0.0 : value;
    }

    public synchronized Map<String, Double> getAllPrices() {
        return new HashMap<>(prices);
    }
}
