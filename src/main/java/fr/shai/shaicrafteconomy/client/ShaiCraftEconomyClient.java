/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.client;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.client.gui.EconomyShopScreen;
import fr.shai.shaicrafteconomy.client.gui.TradingScreen;
import fr.shai.shaicrafteconomy.network.Networking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class ShaiCraftEconomyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ShaiCraftEconomy.SHOP_SCREEN_HANDLER, EconomyShopScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(Networking.BalanceUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof EconomyShopScreen screen) {
                    screen.updateBalance(payload.balance());
                } else if (context.client().currentScreen instanceof TradingScreen screen) {
                    screen.updateBalance(payload.balance());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Networking.MarketUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof EconomyShopScreen screen) {
                    screen.updateMarket(payload.data());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Networking.PortfolioUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof TradingScreen screen) {
                    screen.updatePortfolio(payload.avgPrices());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Networking.InvestmentUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof EconomyShopScreen screen) {
                    screen.updateInvestments(payload.levels());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Networking.CryptoUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof TradingScreen screen) {
                    screen.updatePrices(payload.prices());
                }
            });
        });
    }
}
