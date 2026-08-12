/**
 * ShaiCraft Economy - Leveraged Trading Manager
 * Handles Long/Short positions with leverage and liquidation
 */
package fr.shai.shaicrafteconomy.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class LeverageTradeManager {

    public enum Direction { LONG, SHORT }

    public static class Position {
        public String coinId;
        public Direction direction;
        public double margin;       // Capital initial misé (en €)
        public double leverage;     // Multiplicateur (1–100)
        public double entryPrice;   // Prix d'entrée
        public double size;         // Taille de la position = margin * leverage
        public long openedAt;       // Timestamp d'ouverture

        public Position() {}

        public Position(String coinId, Direction direction, double margin, double leverage, double entryPrice) {
            this.coinId    = coinId;
            this.direction = direction;
            this.margin    = margin;
            this.leverage  = leverage;
            this.entryPrice = entryPrice;
            this.size      = margin * leverage;
            this.openedAt  = System.currentTimeMillis();
        }

        /**
         * Calcule le P&L non-réalisé en €.
         * LONG  : profit si prix monte,  perte si prix baisse
         * SHORT : profit si prix baisse, perte si prix monte
         */
        public double unrealizedPnl(double currentPrice) {
            if (entryPrice <= 0) return 0;
            double priceDiff = currentPrice - entryPrice;
            double rawPnl = (size / entryPrice) * priceDiff;
            return direction == Direction.LONG ? rawPnl : -rawPnl;
        }

        /**
         * Retourne le ratio de perte par rapport à la marge.
         * Si >= 1.0 → liquidation.
         */
        public double lossRatio(double currentPrice) {
            double pnl = unrealizedPnl(currentPrice);
            if (pnl >= 0) return 0;
            return Math.abs(pnl) / margin;
        }

        /** Prix auquel la position sera liquidée (perte totale de la marge). */
        public double liquidationPrice() {
            // Liquidation when |PnL| = margin
            // LONG:  entryPrice - entryPrice/leverage
            // SHORT: entryPrice + entryPrice/leverage
            double move = entryPrice / leverage;
            return direction == Direction.LONG ? entryPrice - move : entryPrice + move;
        }
    }

    // ── Price history for the chart ───────────────────────────────────────────
    private static final Map<String, List<Double>> priceHistory = new LinkedHashMap<>();
    private static final int HISTORY_SIZE = 45; // 45 data points → fits 9 columns × 5 rows chart

    // ── Persistence ───────────────────────────────────────────────────────────
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File dataFile;
    // Map<playerUUID, List<Position>>
    private Map<String, List<Position>> positions = new HashMap<>();

    public LeverageTradeManager() {
        this.dataFile = new File(
            FabricLoader.getInstance().getConfigDir().toFile(),
            "shaicraft-leverage.json"
        );
        // Init history for each coin
        for (String coin : List.of("bitcoin", "ethereum", "solana", "dogecoin")) {
            priceHistory.put(coin, new ArrayList<>());
        }
    }

    public void load() {
        if (!dataFile.exists()) { positions = new HashMap<>(); return; }
        try (FileReader r = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, List<Position>>>(){}.getType();
            Map<String, List<Position>> loaded = GSON.fromJson(r, type);
            if (loaded != null) positions = loaded;
        } catch (IOException e) {
            e.printStackTrace();
            positions = new HashMap<>();
        }
    }

    public void save() {
        try (FileWriter w = new FileWriter(dataFile)) {
            GSON.toJson(positions, w);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ── Price history management ──────────────────────────────────────────────
    public static void recordPrice(String coinId, double price) {
        List<Double> hist = priceHistory.computeIfAbsent(coinId, k -> new ArrayList<>());
        hist.add(price);
        while (hist.size() > HISTORY_SIZE) hist.remove(0);
    }

    public static List<Double> getHistory(String coinId) {
        return Collections.unmodifiableList(
            priceHistory.getOrDefault(coinId, new ArrayList<>())
        );
    }

    // ── Position management ───────────────────────────────────────────────────
    public void openPosition(String playerUuid, Position pos) {
        positions.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(pos);
        save();
    }

    public boolean closePosition(String playerUuid, int index) {
        List<Position> list = positions.get(playerUuid);
        if (list == null || index >= list.size()) return false;
        list.remove(index);
        save();
        return true;
    }

    public List<Position> getPositions(String playerUuid) {
        return positions.getOrDefault(playerUuid, new ArrayList<>());
    }

    /**
     * Check all positions for liquidation. Returns list of liquidated positions
     * (already removed from the map).
     */
    public List<Map.Entry<String, Position>> checkLiquidations(Map<String, Double> currentPrices) {
        List<Map.Entry<String, Position>> liquidated = new ArrayList<>();
        for (var entry : positions.entrySet()) {
            String uuid = entry.getKey();
            List<Position> list = entry.getValue();
            Iterator<Position> it = list.iterator();
            while (it.hasNext()) {
                Position pos = it.next();
                double price = currentPrices.getOrDefault(pos.coinId, pos.entryPrice);
                if (pos.lossRatio(price) >= 1.0) {
                    liquidated.add(Map.entry(uuid, pos));
                    it.remove();
                }
            }
        }
        if (!liquidated.isEmpty()) save();
        return liquidated;
    }
}
