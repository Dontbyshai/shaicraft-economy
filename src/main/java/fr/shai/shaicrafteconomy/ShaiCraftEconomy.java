/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy;
import fr.shai.shaicrafteconomy.command.EconomyCommands;
import fr.shai.shaicrafteconomy.event.MiningListener;

import fr.shai.shaicrafteconomy.economy.EconomyManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.resource.featuretoggle.FeatureFlags;

public class ShaiCraftEconomy implements ModInitializer {
    public static final String MOD_ID = "shaicraft-economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ScreenHandlerType<fr.shai.shaicrafteconomy.gui.EconomyShopScreenHandler> SHOP_SCREEN_HANDLER = 
        Registry.register(Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "shop"), new ScreenHandlerType<>(fr.shai.shaicrafteconomy.gui.EconomyShopScreenHandler::new, FeatureFlags.VANILLA_FEATURES));
    
    private static ShaiCraftEconomy instance;
    private EconomyManager economyManager;
    private fr.shai.shaicrafteconomy.market.MarketManager marketManager;
    private fr.shai.shaicrafteconomy.market.TradeManager tradeManager;
    private fr.shai.shaicrafteconomy.investment.InvestmentManager investmentManager;
    private fr.shai.shaicrafteconomy.market.CryptoManager cryptoManager;
    private fr.shai.shaicrafteconomy.auction.PokemonAuctionManager auctionManager;
    private fr.shai.shaicrafteconomy.market.LeverageTradeManager leverageTradeManager;
    private fr.shai.shaicrafteconomy.trade.TradeManager tradeSessionManager;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing ShaiCraft Economy by Shaï...");

        economyManager = new EconomyManager();
        economyManager.load();

        marketManager = new fr.shai.shaicrafteconomy.market.MarketManager();
        marketManager.load();

        tradeManager = new fr.shai.shaicrafteconomy.market.TradeManager();
        tradeManager.load();

        investmentManager = new fr.shai.shaicrafteconomy.investment.InvestmentManager();
        investmentManager.load();

        cryptoManager = new fr.shai.shaicrafteconomy.market.CryptoManager();
        new Thread(() -> cryptoManager.fetchPrices()).start(); // Initial fetch in background

        auctionManager = new fr.shai.shaicrafteconomy.auction.PokemonAuctionManager();
        auctionManager.load();

        leverageTradeManager = new fr.shai.shaicrafteconomy.market.LeverageTradeManager();
        leverageTradeManager.load();

        tradeSessionManager = new fr.shai.shaicrafteconomy.trade.TradeManager();

        fr.shai.shaicrafteconomy.network.Networking.register();

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(fr.shai.shaicrafteconomy.network.Networking.PurchasePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                String id = payload.id();
                double currentPrice = marketManager.getPrice(id);
                if (currentPrice <= 0) currentPrice = payload.price(); // Fallback

                if (payload.action().equals("item")) {
                    net.minecraft.item.Item item = Registries.ITEM.get(Identifier.of(id));
                    if (economyManager.removeBalance(player.getUuid(), currentPrice)) {
                        player.getInventory().insertStack(new net.minecraft.item.ItemStack(item));
                        syncBalance(player);
                    }
                } else if (payload.action().equals("sell")) {
                    net.minecraft.item.Item item = Registries.ITEM.get(Identifier.of(id));
                    if (player.getInventory().count(item) > 0) {
                        player.getInventory().remove(stack -> stack.isOf(item), 1, player.getInventory());
                        economyManager.addBalance(player.getUuid(), currentPrice);
                        syncBalance(player);
                    }
                } else if (payload.action().equals("pokemon")) {
                    if (fr.shai.shaicrafteconomy.integration.CobblemonIntegration.buyPokemon(player, id, currentPrice) == 1) {
                        syncBalance(player);
                    }
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(fr.shai.shaicrafteconomy.network.Networking.CryptoActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                String coinId = payload.coinId();
                double price = payload.price();
                double amount = payload.amount();

                if (payload.action().equals("buy")) {
                    double cost = price * amount;
                    if (economyManager.removeBalance(player.getUuid(), cost)) {
                        tradeManager.recordBuy(player.getUuid(), coinId, price, amount);
                        syncBalance(player);
                        syncPortfolio(player);
                    }
                } else if (payload.action().equals("sell")) {
                    if (tradeManager.recordSell(player.getUuid(), coinId, price, amount)) {
                        economyManager.addBalance(player.getUuid(), price * amount);
                        syncBalance(player);
                        syncPortfolio(player);
                    }
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(fr.shai.shaicrafteconomy.network.Networking.InvestmentActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                String typeId = payload.typeId();
                fr.shai.shaicrafteconomy.investment.InvestmentType type = null;
                for (fr.shai.shaicrafteconomy.investment.InvestmentType t : fr.shai.shaicrafteconomy.investment.InvestmentType.values()) {
                    if (t.getId().equals(typeId)) { type = t; break; }
                }
                if (type == null) return;

                int currentLevel = investmentManager.getLevel(player.getUuid(), typeId);
                double cost = type.getUpgradeCost(currentLevel);

                if (economyManager.removeBalance(player.getUuid(), cost)) {
                    investmentManager.setLevel(player.getUuid(), typeId, currentLevel + 1);
                    syncInvestments(player);
                    syncBalance(player);
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            economyManager.load();
            fr.shai.shaicrafteconomy.network.Networking.sendBalanceUpdate(handler.getPlayer(), economyManager.getBalance(handler.getPlayer().getUuid()));
            syncMarket(server);
            syncCrypto(server);
            syncPortfolio(handler.getPlayer());

            handler.getPlayer().sendMessage(Text.literal("§b[ShaiCraft Economy] §7Bienvenue ! Mod créé par §6Shaï§7. Use /shop for survival or /trading for crypto."));
        });

        // Passive Income Ticker (every 5 minutes = 6000 ticks)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 6000 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    double yield = investmentManager.calculateTotalYield(player.getUuid());
                    if (yield > 0) {
                        economyManager.addBalance(player.getUuid(), yield);
                        syncBalance(player);
                        player.sendMessage(Text.translatable("msg.shaicraft-economy.passive_income", String.format("%.2f", yield)).formatted(Formatting.GOLD));
                    }
                }
            }
            
            // Crypto Poll Ticker (every 5 seconds = 100 ticks)
            if (server.getTicks() % 100 == 0) {
                new Thread(() -> {
                    cryptoManager.fetchPrices();
                    syncCrypto(server);
                    // Record price history for charts
                    java.util.Map<String, Double> prices = cryptoManager.getAllPrices();
                    prices.forEach(fr.shai.shaicrafteconomy.market.LeverageTradeManager::recordPrice);
                    // Check liquidations
                    java.util.List<java.util.Map.Entry<String, fr.shai.shaicrafteconomy.market.LeverageTradeManager.Position>> liquidated
                        = leverageTradeManager.checkLiquidations(prices);
                    for (var liq : liquidated) {
                        java.util.UUID uuid = java.util.UUID.fromString(liq.getKey());
                        ServerPlayerEntity liqPlayer = server.getPlayerManager().getPlayer(uuid);
                        if (liqPlayer != null) {
                            liqPlayer.sendMessage(net.minecraft.text.Text.literal(
                                "💥 LIQUIDATION ! Votre position " +
                                (liq.getValue().direction == fr.shai.shaicrafteconomy.market.LeverageTradeManager.Direction.LONG ? "LONG" : "SHORT") +
                                " " + liq.getValue().coinId + " x" + (int)liq.getValue().leverage +
                                " a été liquidée. Marge de " + String.format("%,.0f", liq.getValue().margin) + "€ perdue."
                            ).formatted(net.minecraft.util.Formatting.DARK_RED, net.minecraft.util.Formatting.BOLD));
                        }
                    }
                }).start();
            }
        });

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EconomyCommands.register(dispatcher);
        });

        // Chat intercept for trade money input
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            boolean handled = tradeSessionManager.handleChatInput(sender, message.getContent().getString());
            return !handled; // If handled by trade manager, cancel chat broadcast
        });

        MiningListener.register();

        LOGGER.info("ShaiCraft Economy initialized!");
    }

    private void syncMarket(net.minecraft.server.MinecraftServer server) {
        fr.shai.shaicrafteconomy.network.Networking.sendMarketUpdate(server, marketManager.getAllMarketData());
    }

    public void syncPortfolio(ServerPlayerEntity player) {
        fr.shai.shaicrafteconomy.network.Networking.sendPortfolioUpdate(player, tradeManager.getAverages(player.getUuid()));
    }

    public void syncInvestments(ServerPlayerEntity player) {
        fr.shai.shaicrafteconomy.network.Networking.sendInvestmentUpdate(player, investmentManager.getInvestments(player.getUuid()));
    }

    public void syncBalance(ServerPlayerEntity player) {
        fr.shai.shaicrafteconomy.network.Networking.sendBalanceUpdate(player, economyManager.getBalance(player.getUuid()));
    }

    public void syncCrypto(net.minecraft.server.MinecraftServer server) {
        fr.shai.shaicrafteconomy.network.Networking.sendCryptoUpdate(server, cryptoManager.getAllPrices());
    }

    public static ShaiCraftEconomy getInstance() {
        return instance;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public fr.shai.shaicrafteconomy.market.MarketManager getMarketManager() {
        return marketManager;
    }
    
    public fr.shai.shaicrafteconomy.market.TradeManager getTradeManager() {
        return tradeManager;
    }
    
    public fr.shai.shaicrafteconomy.investment.InvestmentManager getInvestmentManager() {
        return investmentManager;
    }

    public fr.shai.shaicrafteconomy.market.CryptoManager getCryptoManager() {
        return cryptoManager;
    }

    public fr.shai.shaicrafteconomy.auction.PokemonAuctionManager getAuctionManager() {
        return auctionManager;
    }

    public fr.shai.shaicrafteconomy.market.LeverageTradeManager getLeverageTradeManager() {
        return leverageTradeManager;
    }

    public fr.shai.shaicrafteconomy.trade.TradeManager getTradeSessionManager() {
        return tradeSessionManager;
    }
}
