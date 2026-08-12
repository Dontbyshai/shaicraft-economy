/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.market;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class CryptoManager {
    private static final String BINANCE_API = "https://api.binance.com/api/v3/ticker/price?symbols=[\"BTCUSDT\",\"ETHUSDT\",\"SOLUSDT\",\"DOGEUSDT\"]";
    private static final double USD_TO_EUR = 0.92; // Conversion factor for mod consistency
    private final Map<String, Double> prices = new HashMap<>();

    public CryptoManager() {
        // Default fallbacks (EUR)
        prices.put("bitcoin", 62000.0);
        prices.put("ethereum", 3200.0);
        prices.put("solana", 150.0);
        prices.put("dogecoin", 0.14);
    }

    public void fetchPrices() {
        try {
            URL url = new URL(BINANCE_API);
            InputStreamReader reader = new InputStreamReader(url.openStream());
            // Binance returns a List of objects: [{"symbol":"BTCUSDT","price":"60000.00"}, ...]
            java.util.List<Map<String, String>> data = new Gson().fromJson(reader, new TypeToken<java.util.List<Map<String, String>>>() {}.getType());
            
            if (data != null) {
                for (Map<String, String> entry : data) {
                    String symbol = entry.get("symbol");
                    double priceUsd = Double.parseDouble(entry.get("price"));
                    double priceEur = priceUsd * USD_TO_EUR;

                    if (symbol.equals("BTCUSDT")) prices.put("bitcoin", priceEur);
                    else if (symbol.equals("ETHUSDT")) prices.put("ethereum", priceEur);
                    else if (symbol.equals("SOLUSDT")) prices.put("solana", priceEur);
                    else if (symbol.equals("DOGEUSDT")) prices.put("dogecoin", priceEur);
                }
            }
        } catch (Exception e) {
            System.out.println("[ShaiCraft Economy] Failed to fetch Binance prices. Using offline fallbacks.");
        }
    }

    public Double getPrice(String id) {
        return prices.getOrDefault(id, 0.0);
    }

    public Map<String, Double> getAllPrices() {
        return prices;
    }
}
