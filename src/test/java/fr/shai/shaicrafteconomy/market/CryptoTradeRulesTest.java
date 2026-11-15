package fr.shai.shaicrafteconomy.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CryptoTradeRulesTest {
    @Test
    void leCoutVientUniquementDuPrixServeur() {
        assertNull(CryptoTradeRules.rejection("buy", "bitcoin", 0.1, 62_000));
        assertEquals(6_200, CryptoTradeRules.cost(62_000, 0.1), 0.001);
        // Un client qui annoncerait 1 € n'a aucun paramètre pour le faire passer.
        assertEquals(62_000, CryptoTradeRules.cost(62_000, 1), 0.001);
    }

    @Test
    void cryptoInconnueOuMontantAbsurdeRefuses() {
        assertNotNull(CryptoTradeRules.rejection("buy", "notacoin", 1, 10));
        assertNotNull(CryptoTradeRules.rejection("steal", "bitcoin", 1, 10));
        assertNotNull(CryptoTradeRules.rejection("buy", "bitcoin", 0, 10));
        assertNotNull(CryptoTradeRules.rejection("buy", "bitcoin", -1, 10));
        assertNotNull(CryptoTradeRules.rejection("buy", "bitcoin", Double.NaN, 10));
        assertNotNull(CryptoTradeRules.rejection("buy", "bitcoin", 1_000_000, 10));
        assertNotNull(CryptoTradeRules.rejection("sell", "ethereum", 1, 0));
        assertNotNull(CryptoTradeRules.rejection("sell", "ethereum", 1, -5));
    }

    @Test
    void lesQuatreCryptosOntUnPrixDeSecours() {
        for (String coin : CryptoTradeRules.COINS) {
            assertNotNull(CryptoTradeRules.binanceSymbol(coin));
            assertEquals(coin, CryptoTradeRules.coinForSymbol(CryptoTradeRules.binanceSymbol(coin)));
            assertNull(CryptoTradeRules.rejection("buy", coin, 0.01, CryptoTradeRules.fallbackPrice(coin)));
        }
    }
}
