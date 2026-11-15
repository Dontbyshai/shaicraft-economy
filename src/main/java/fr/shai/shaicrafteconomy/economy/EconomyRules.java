/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.economy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calculs d'argent sans Minecraft, pour pouvoir les tester.
 * La taxe ne s'applique qu'aux paiements entre joueurs (/pay).
 */
public final class EconomyRules {
    public static final double MAX_MONEY = 1_000_000_000_000.0;

    private EconomyRules() {}

    public static double round(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    public static boolean isPositiveMoney(double amount) {
        return Double.isFinite(amount) && amount >= 0.01 && amount <= MAX_MONEY;
    }

    public static boolean isNonNegativeMoney(double amount) {
        return Double.isFinite(amount) && amount >= 0.0 && amount <= MAX_MONEY;
    }

    /** Part prélevée sur un paiement, arrondie au centime. */
    public static double tax(double gross, double taxPercent) {
        if (!Double.isFinite(gross) || gross <= 0) return 0.0;
        double percent = taxPercent;
        if (!Double.isFinite(percent) || percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        return round(gross * percent / 100.0);
    }

    /** Ce que le destinataire reçoit vraiment. */
    public static double netAfterTax(double gross, double taxPercent) {
        if (!Double.isFinite(gross) || gross <= 0) return 0.0;
        double net = gross - tax(gross, taxPercent);
        return net < 0 ? 0.0 : net;
    }

    public static List<Map.Entry<UUID, Double>> topBalances(Map<UUID, Double> balances, int limit) {
        return balances.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
            .sorted(Comparator.comparingDouble((Map.Entry<UUID, Double> entry) -> entry.getValue()).reversed())
            .limit(Math.max(0, limit))
            .toList();
    }

    /** 1 = le plus riche. */
    public static int rankOf(Map<UUID, Double> balances, UUID uuid) {
        double mine = balances.getOrDefault(uuid, 0.0);
        int better = 0;
        for (double value : balances.values()) {
            if (value > mine) better++;
        }
        return better + 1;
    }
}
