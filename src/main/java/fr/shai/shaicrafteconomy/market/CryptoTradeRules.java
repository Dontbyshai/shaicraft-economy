/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.market;

import fr.shai.shaicrafteconomy.economy.EconomyRules;

import java.util.List;

/**
 * Règles du trading spot. Le prix du client n'entre jamais dans le calcul :
 * seul le prix connu du serveur est accepté.
 */
public final class CryptoTradeRules {
    public static final List<String> COINS = List.of("bitcoin", "ethereum", "solana", "dogecoin");
    public static final double MIN_AMOUNT = 0.00000001;
    public static final double MAX_AMOUNT = 1_000.0;

    private CryptoTradeRules() {}

    public static String binanceSymbol(String coinId) {
        return switch (coinId) {
            case "bitcoin" -> "BTCUSDT";
            case "ethereum" -> "ETHUSDT";
            case "solana" -> "SOLUSDT";
            case "dogecoin" -> "DOGEUSDT";
            default -> null;
        };
    }

    public static String coinForSymbol(String symbol) {
        if (symbol == null) return null;
        for (String coin : COINS) {
            if (symbol.equals(binanceSymbol(coin))) return coin;
        }
        return null;
    }

    public static double fallbackPrice(String coinId) {
        return switch (coinId) {
            case "bitcoin" -> 62_000.0;
            case "ethereum" -> 3_200.0;
            case "solana" -> 150.0;
            case "dogecoin" -> 0.14;
            default -> 0.0;
        };
    }

    public static double cost(double serverPrice, double amount) {
        return serverPrice * amount;
    }

    /**
     * @return null si l'ordre est acceptable, sinon la clé de traduction du refus.
     */
    public static String rejection(String action, String coinId, double amount, double serverPrice) {
        if (!"buy".equals(action) && !"sell".equals(action)) {
            return "msg.shaicraft-economy.crypto_unknown_action";
        }
        if (coinId == null || !COINS.contains(coinId)) {
            return "msg.shaicraft-economy.crypto_unknown";
        }
        if (!Double.isFinite(amount) || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            return "msg.shaicraft-economy.crypto_bad_amount";
        }
        if (!Double.isFinite(serverPrice) || serverPrice <= 0) {
            return "msg.shaicraft-economy.crypto_price_unavailable";
        }
        double total = cost(serverPrice, amount);
        if (!Double.isFinite(total) || total <= 0 || total > EconomyRules.MAX_MONEY) {
            return "msg.shaicraft-economy.crypto_bad_amount";
        }
        return null;
    }
}
