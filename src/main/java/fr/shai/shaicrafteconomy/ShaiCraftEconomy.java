/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy;

import fr.shai.shaicrafteconomy.auction.PokemonAuctionManager;
import fr.shai.shaicrafteconomy.command.EconomyCommands;
import fr.shai.shaicrafteconomy.economy.EconomyManager;
import fr.shai.shaicrafteconomy.economy.ModConfig;
import fr.shai.shaicrafteconomy.economy.TransactionLog;
import fr.shai.shaicrafteconomy.event.MiningListener;
import fr.shai.shaicrafteconomy.integration.CobblemonIntegration;
import fr.shai.shaicrafteconomy.investment.InvestmentManager;
import fr.shai.shaicrafteconomy.investment.InvestmentType;
import fr.shai.shaicrafteconomy.market.CryptoManager;
import fr.shai.shaicrafteconomy.market.CryptoTradeRules;
import fr.shai.shaicrafteconomy.market.LeverageTradeManager;
import fr.shai.shaicrafteconomy.market.MarketManager;
import fr.shai.shaicrafteconomy.market.TradeManager;
import fr.shai.shaicrafteconomy.network.Networking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShaiCraftEconomy implements ModInitializer {
    public static final String MOD_ID = "shaicraft-economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ScreenHandlerType<fr.shai.shaicrafteconomy.gui.EconomyShopScreenHandler> SHOP_SCREEN_HANDLER =
        Registry.register(Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "shop"),
            new ScreenHandlerType<>(fr.shai.shaicrafteconomy.gui.EconomyShopScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    private static ShaiCraftEconomy instance;
    private EconomyManager economyManager;
    private MarketManager marketManager;
    private TradeManager tradeManager;
    private InvestmentManager investmentManager;
    private CryptoManager cryptoManager;
    private PokemonAuctionManager auctionManager;
    private LeverageTradeManager leverageTradeManager;
    private fr.shai.shaicrafteconomy.trade.TradeManager tradeSessionManager;
    private TransactionLog transactionLog;
    private final AtomicBoolean cryptoFetchRunning = new AtomicBoolean(false);
    private int ticksSinceSave;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing ShaiCraft Economy by Shaï...");

        ModConfig.load();

        economyManager = new EconomyManager();
        economyManager.load();

        transactionLog = new TransactionLog();
        transactionLog.load();

        marketManager = new MarketManager();
        marketManager.load();

        tradeManager = new TradeManager();
        tradeManager.load();

        investmentManager = new InvestmentManager();
        investmentManager.load();

        cryptoManager = new CryptoManager();

        auctionManager = new PokemonAuctionManager();
        auctionManager.load();

        leverageTradeManager = new LeverageTradeManager();
        leverageTradeManager.load();

        tradeSessionManager = new fr.shai.shaicrafteconomy.trade.TradeManager();

        Networking.register();

        ServerPlayNetworking.registerGlobalReceiver(Networking.PurchasePayload.ID, (payload, context) -> {
            context.server().execute(() -> handlePurchase(context.player(), payload.action(), payload.id()));
        });

        ServerPlayNetworking.registerGlobalReceiver(Networking.CryptoActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleCrypto(context.player(), payload.action(), payload.coinId(), payload.amount()));
        });

        ServerPlayNetworking.registerGlobalReceiver(Networking.InvestmentActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleInvestment(context.player(), payload.typeId()));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // On ne relit pas le fichier ici : ça écraserait les paiements encore en mémoire.
            economyManager.rememberName(player.getUuid(), player.getGameProfile().getName());
            Networking.sendBalanceUpdate(player, economyManager.getBalance(player.getUuid()));
            syncMarket(server);
            syncCrypto(server);
            syncPortfolio(player);
            player.sendMessage(Text.translatable("msg.shaicraft-economy.welcome"));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> savePersistentData());

        ServerLifecycleEvents.SERVER_STARTING.register(starting -> this.server = starting);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> savePersistentData());
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> this.server = null);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (cryptoManager.applyPending()) {
                onCryptoPricesUpdated(server);
            }

            // Toutes les 5 secondes : télécharger Binance hors du thread du jeu.
            if (server.getTicks() % 100 == 0) {
                scheduleCryptoFetch();
            }

            ticksSinceSave++;
            if (ticksSinceSave >= ModConfig.get().autosaveSeconds * 20) {
                ticksSinceSave = 0;
                savePersistentData();
            }

            // Revenu passif toutes les 5 minutes.
            if (server.getTicks() > 0 && server.getTicks() % 6000 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    double yield = investmentManager.calculateTotalYield(player.getUuid());
                    if (yield > 0) {
                        economyManager.addBalance(player.getUuid(), yield);
                        transactionLog.add(player.getUuid(), "PASSIVE", yield, "");
                        syncBalance(player);
                        player.sendMessage(Text.translatable("msg.shaicraft-economy.passive_income", String.format("%.2f", yield)).formatted(Formatting.GOLD));
                    }
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EconomyCommands.register(dispatcher));

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            boolean handled = tradeSessionManager.handleChatInput(sender, message.getContent().getString());
            return !handled;
        });

        MiningListener.register();

        LOGGER.info("ShaiCraft Economy initialized!");
    }

    private void handlePurchase(ServerPlayerEntity player, String action, String id) {
        if (action == null || id == null || id.length() > 64) return;

        if ("pokemon".equals(action)) {
            if (!id.matches("[a-z0-9_]+")) {
                LOGGER.warn("Achat Pokémon refusé pour {} : identifiant invalide.", player.getName().getString());
                return;
            }
            double price = marketManager.getPrice(id);
            if (price <= 0) {
                player.sendMessage(Text.translatable("msg.shaicraft-economy.price_unavailable").formatted(Formatting.RED));
                return;
            }
            if (CobblemonIntegration.buyPokemon(player, id, price) == 1) {
                syncBalance(player);
            }
            return;
        }

        if (!"item".equals(action) && !"sell".equals(action)) return;

        double price = marketManager.getPrice(id);
        if (price <= 0) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.price_unavailable").formatted(Formatting.RED));
            return;
        }

        Identifier identifier;
        try {
            identifier = Identifier.of(id);
        } catch (IllegalArgumentException ex) {
            return;
        }
        Item item = Registries.ITEM.get(identifier);
        if (item == Items.AIR) return;

        if ("item".equals(action)) {
            if (!economyManager.removeBalance(player.getUuid(), price)) {
                player.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
                return;
            }
            if (!player.getInventory().insertStack(new ItemStack(item))) {
                economyManager.addBalance(player.getUuid(), price);
                player.sendMessage(Text.translatable("msg.shaicraft-economy.inventory_full").formatted(Formatting.RED));
                return;
            }
            transactionLog.add(player.getUuid(), "SHOP_BUY", -price, id);
            syncBalance(player);
            player.sendMessage(Text.translatable("msg.shaicraft-economy.buy_success", item.getName().getString(), String.format("%.2f", price)).formatted(Formatting.GREEN));
            return;
        }

        if (player.getInventory().count(item) <= 0) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.missing_item").formatted(Formatting.RED));
            return;
        }
        player.getInventory().remove(stack -> stack.isOf(item), 1, player.getInventory());
        economyManager.addBalance(player.getUuid(), price);
        transactionLog.add(player.getUuid(), "SHOP_SELL", price, id);
        syncBalance(player);
        player.sendMessage(Text.translatable("msg.shaicraft-economy.sell_success", 1, item.getName().getString(), String.format("%.2f", price)).formatted(Formatting.GOLD));
    }

    private void handleCrypto(ServerPlayerEntity player, String action, String coinId, double amount) {
        double serverPrice = cryptoManager.getPrice(coinId);
        String error = CryptoTradeRules.rejection(action, coinId, amount, serverPrice);
        if (error != null) {
            if ("msg.shaicraft-economy.crypto_unknown".equals(error) || "msg.shaicraft-economy.crypto_unknown_action".equals(error)) {
                LOGGER.warn("Action crypto refusée pour {} : {} {} {}", player.getName().getString(), action, coinId, amount);
            }
            player.sendMessage(Text.translatable(error).formatted(Formatting.RED));
            return;
        }

        double cost = CryptoTradeRules.cost(serverPrice, amount);
        if ("buy".equals(action)) {
            if (!economyManager.removeBalance(player.getUuid(), cost)) {
                player.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
                return;
            }
            tradeManager.recordBuy(player.getUuid(), coinId, serverPrice, amount);
            transactionLog.add(player.getUuid(), "CRYPTO_BUY", -cost, coinId + " x" + amount);
            syncBalance(player);
            syncPortfolio(player);
            player.sendMessage(Text.translatable("msg.shaicraft-economy.crypto_buy",
                String.format("%.4f", amount), coinId, String.format("%.2f", serverPrice), String.format("%.2f", cost)).formatted(Formatting.GREEN));
            return;
        }

        if (tradeManager.getAmount(player.getUuid(), coinId) + 1.0e-8 < amount
            || !tradeManager.recordSell(player.getUuid(), coinId, serverPrice, amount)) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.crypto_no_holding").formatted(Formatting.RED));
            return;
        }
        economyManager.addBalance(player.getUuid(), cost);
        transactionLog.add(player.getUuid(), "CRYPTO_SELL", cost, coinId + " x" + amount);
        syncBalance(player);
        syncPortfolio(player);
        player.sendMessage(Text.translatable("msg.shaicraft-economy.crypto_sell",
            String.format("%.4f", amount), coinId, String.format("%.2f", serverPrice), String.format("%.2f", cost)).formatted(Formatting.GOLD));
    }

    private void handleInvestment(ServerPlayerEntity player, String typeId) {
        InvestmentType type = null;
        for (InvestmentType candidate : InvestmentType.values()) {
            if (candidate.getId().equals(typeId)) {
                type = candidate;
                break;
            }
        }
        if (type == null) return;

        int currentLevel = investmentManager.getLevel(player.getUuid(), typeId);
        double cost = type.getUpgradeCost(currentLevel);
        if (!economyManager.removeBalance(player.getUuid(), cost)) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
            return;
        }
        investmentManager.setLevel(player.getUuid(), typeId, currentLevel + 1);
        transactionLog.add(player.getUuid(), "INVEST", -cost, type.getDisplayName());
        syncInvestments(player);
        syncBalance(player);
    }

    /**
     * Le thread Binance ne fait que déposer les prix.
     * Liquidations, messages et paquets restent sur le thread serveur.
     */
    private void scheduleCryptoFetch() {
        if (!cryptoFetchRunning.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            try {
                var fetched = cryptoManager.downloadPrices();
                if (fetched != null) cryptoManager.queuePrices(fetched);
            } catch (Exception e) {
                LOGGER.warn("Récupération Binance interrompue : {}", e.toString());
            } finally {
                cryptoFetchRunning.set(false);
            }
        }, "shaicraft-binance");
        thread.setDaemon(true);
        thread.start();
    }

    private void onCryptoPricesUpdated(MinecraftServer server) {
        var prices = cryptoManager.getAllPrices();
        prices.forEach(LeverageTradeManager::recordPrice);
        syncCrypto(server);
        var liquidated = leverageTradeManager.checkLiquidations(prices);
        for (var liq : liquidated) {
            UUID uuid = UUID.fromString(liq.getKey());
            var pos = liq.getValue();
            String direction = pos.direction == LeverageTradeManager.Direction.LONG ? "LONG" : "SHORT";
            transactionLog.add(uuid, "LEVERAGE_LIQ", -pos.margin, direction + " " + pos.coinId + " x" + (int) pos.leverage);
            ServerPlayerEntity liqPlayer = server.getPlayerManager().getPlayer(uuid);
            if (liqPlayer != null) {
                liqPlayer.sendMessage(Text.translatable("msg.shaicraft-economy.liquidation",
                    direction, pos.coinId, (int) pos.leverage, String.format("%.2f", pos.margin)
                ).formatted(Formatting.DARK_RED, Formatting.BOLD));
            }
        }
        if (!liquidated.isEmpty()) savePersistentData();
    }

    public void savePersistentData() {
        boolean wrote = false;
        if (economyManager != null) wrote |= economyManager.saveIfDirty();
        if (transactionLog != null) wrote |= transactionLog.saveIfDirty();
        if (tradeManager != null) wrote |= tradeManager.saveIfDirty();
        if (investmentManager != null) wrote |= investmentManager.saveIfDirty();
        if (leverageTradeManager != null) wrote |= leverageTradeManager.saveIfDirty();
        if (auctionManager != null) wrote |= auctionManager.saveIfDirty();
        if (wrote) LOGGER.info("Économie sauvegardée.");
    }

    private void syncMarket(MinecraftServer server) {
        Networking.sendMarketUpdate(server, marketManager.getAllMarketData());
    }

    public void syncPortfolio(ServerPlayerEntity player) {
        Networking.sendPortfolioUpdate(player, tradeManager.getAverages(player.getUuid()));
    }

    public void syncInvestments(ServerPlayerEntity player) {
        Networking.sendInvestmentUpdate(player, investmentManager.getInvestments(player.getUuid()));
    }

    public void syncBalance(ServerPlayerEntity player) {
        Networking.sendBalanceUpdate(player, economyManager.getBalance(player.getUuid()));
    }

    public void syncCrypto(MinecraftServer server) {
        Networking.sendCryptoUpdate(server, cryptoManager.getAllPrices());
    }

    public static ShaiCraftEconomy getInstance() {
        return instance;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }

    public InvestmentManager getInvestmentManager() {
        return investmentManager;
    }

    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }

    public PokemonAuctionManager getAuctionManager() {
        return auctionManager;
    }

    public LeverageTradeManager getLeverageTradeManager() {
        return leverageTradeManager;
    }

    public fr.shai.shaicrafteconomy.trade.TradeManager getTradeSessionManager() {
        return tradeSessionManager;
    }

    public TransactionLog getTransactionLog() {
        return transactionLog;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
