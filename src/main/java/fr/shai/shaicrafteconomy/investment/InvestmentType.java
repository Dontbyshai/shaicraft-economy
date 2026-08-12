/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.investment;

public enum InvestmentType {
    DIAMOND_MINE("diamond_mine", 500.0, 10.0, "Diamond Mine"),
    IRON_FARM("iron_farm", 100.0, 2.0, "Iron Farm"),
    POKEMON_BREEDERY("pokemon_breedery", 2000.0, 50.0, "Pokémon Breeding");

    private final String id;
    private final double baseCost;
    private final double baseYield;
    private final String displayName;

    InvestmentType(String id, double baseCost, double baseYield, String displayName) {
        this.id = id;
        this.baseCost = baseCost;
        this.baseYield = baseYield;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public double getBaseCost() { return baseCost; }
    public double getBaseYield() { return baseYield; }
    public String getDisplayName() { return displayName; }

    public double getUpgradeCost(int currentLevel) {
        return baseCost * Math.pow(1.5, currentLevel);
    }

    public double getYield(int level) {
        return baseYield * level;
    }
}
