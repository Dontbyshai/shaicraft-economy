package fr.shai.shaicrafteconomy.economy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyRulesTest {
    @Test
    void taxeDeDeuxPourcentsSurUnPaiement() {
        assertEquals(2.0, EconomyRules.tax(100, 2), 0.001);
        assertEquals(98.0, EconomyRules.netAfterTax(100, 2), 0.001);
    }

    @Test
    void pasDeTaxeSiLeReglageEstAZero() {
        assertEquals(0.0, EconomyRules.tax(50, 0), 0.001);
        assertEquals(50.0, EconomyRules.netAfterTax(50, 0), 0.001);
    }

    @Test
    void unToutPetitPaiementNeCreePasUneTaxeNegative() {
        assertTrue(EconomyRules.netAfterTax(0.01, 2) > 0);
        assertTrue(EconomyRules.tax(0.01, 2) >= 0);
    }

    @Test
    void montantsInfinisOuNegatifsRefuses() {
        assertFalse(EconomyRules.isPositiveMoney(Double.NaN));
        assertFalse(EconomyRules.isPositiveMoney(Double.POSITIVE_INFINITY));
        assertFalse(EconomyRules.isPositiveMoney(0));
        assertFalse(EconomyRules.isPositiveMoney(-5));
        assertTrue(EconomyRules.isNonNegativeMoney(0));
        assertTrue(EconomyRules.isPositiveMoney(10));
    }

    @Test
    void classementDuPlusRicheAuPlusPauvre() {
        UUID riche = UUID.randomUUID();
        UUID moyen = UUID.randomUUID();
        UUID pauvre = UUID.randomUUID();
        Map<UUID, Double> soldes = Map.of(moyen, 20.0, riche, 100.0, pauvre, 0.0);
        var top = EconomyRules.topBalances(soldes, 10);
        assertEquals(2, top.size());
        assertEquals(riche, top.get(0).getKey());
        assertEquals(moyen, top.get(1).getKey());
        assertEquals(1, EconomyRules.rankOf(soldes, riche));
        assertEquals(2, EconomyRules.rankOf(soldes, moyen));
    }
}
