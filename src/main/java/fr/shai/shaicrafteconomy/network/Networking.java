/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.network;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.market.MarketManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class Networking {
    public static final Identifier BALANCE_UPDATE_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "balance_update");
    public record BalanceUpdatePayload(double balance) implements CustomPayload {
        public static final CustomPayload.Id<BalanceUpdatePayload> ID = new CustomPayload.Id<>(BALANCE_UPDATE_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier PURCHASE_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "purchase");
    /** Le prix n'est pas dans le paquet : le serveur le lit dans MarketManager. */
    public record PurchasePayload(String action, String id) implements CustomPayload {
        public static final CustomPayload.Id<PurchasePayload> ID = new CustomPayload.Id<>(PURCHASE_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier MARKET_UPDATE_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "market_update");
    public record MarketUpdatePayload(Map<String, MarketManager.MarketItem> data) implements CustomPayload {
        public static final CustomPayload.Id<MarketUpdatePayload> ID = new CustomPayload.Id<>(MARKET_UPDATE_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier PORTFOLIO_UPDATE_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "portfolio_update");
    public record PortfolioUpdatePayload(Map<String, Double> avgPrices) implements CustomPayload {
        public static final CustomPayload.Id<PortfolioUpdatePayload> ID = new CustomPayload.Id<>(PORTFOLIO_UPDATE_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier INVESTMENT_UPDATE_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "investment_update");
    public record InvestmentUpdatePayload(Map<String, Integer> levels) implements CustomPayload {
        public static final CustomPayload.Id<InvestmentUpdatePayload> ID = new CustomPayload.Id<>(INVESTMENT_UPDATE_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier INVESTMENT_ACTION_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "investment_action");
    public record InvestmentActionPayload(String typeId) implements CustomPayload {
        public static final CustomPayload.Id<InvestmentActionPayload> ID = new CustomPayload.Id<>(INVESTMENT_ACTION_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier CRYPTO_UPDATE_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "crypto_update");
    public record CryptoUpdatePayload(Map<String, Double> prices) implements CustomPayload {
        public static final CustomPayload.Id<CryptoUpdatePayload> ID = new CustomPayload.Id<>(CRYPTO_UPDATE_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier CRYPTO_ACTION_ID = Identifier.of(ShaiCraftEconomy.MOD_ID, "crypto_action");
    /**
     * Achat ou vente de crypto.
     * Le prix n'est volontairement pas envoyé par le client : un client modifié
     * pourrait sinon acheter du bitcoin à 1 €. CryptoManager fait foi.
     */
    public record CryptoActionPayload(String action, String coinId, double amount) implements CustomPayload {
        public static final CustomPayload.Id<CryptoActionPayload> ID = new CustomPayload.Id<>(CRYPTO_ACTION_ID);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PacketCodec<RegistryByteBuf, BalanceUpdatePayload> balanceCodecCorrect = PacketCodec.<RegistryByteBuf, BalanceUpdatePayload>of(
            (payload, buf) -> buf.writeDouble(payload.balance()),
            buf -> new BalanceUpdatePayload(buf.readDouble())
        );

        PacketCodec<RegistryByteBuf, PurchasePayload> purchaseCodec = PacketCodec.<RegistryByteBuf, PurchasePayload>of(
            (payload, buf) -> {
                buf.writeString(payload.action());
                buf.writeString(payload.id());
            },
            buf -> new PurchasePayload(buf.readString(), buf.readString())
        );

        PacketCodec<RegistryByteBuf, MarketUpdatePayload> marketCodec = PacketCodec.<RegistryByteBuf, MarketUpdatePayload>of(
            (payload, buf) -> {
                buf.writeInt(payload.data().size());
                payload.data().forEach((id, item) -> {
                    buf.writeString(id);
                    buf.writeDouble(item.currentPrice);
                    buf.writeDouble(item.lastChangePercent);
                });
            },
            buf -> {
                int size = buf.readInt();
                Map<String, MarketManager.MarketItem> data = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    String id = buf.readString();
                    MarketManager.MarketItem item = new MarketManager.MarketItem(0.0);
                    item.currentPrice = buf.readDouble();
                    item.lastChangePercent = buf.readDouble();
                    data.put(id, item);
                }
                return new MarketUpdatePayload(data);
            }
        );

        PacketCodec<RegistryByteBuf, PortfolioUpdatePayload> portfolioCodec = PacketCodec.<RegistryByteBuf, PortfolioUpdatePayload>of(
            (payload, buf) -> {
                buf.writeInt(payload.avgPrices().size());
                payload.avgPrices().forEach((id, price) -> {
                    buf.writeString(id);
                    buf.writeDouble(price);
                });
            },
            buf -> {
                int size = buf.readInt();
                Map<String, Double> data = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    data.put(buf.readString(), buf.readDouble());
                }
                return new PortfolioUpdatePayload(data);
            }
        );

        PacketCodec<RegistryByteBuf, InvestmentUpdatePayload> investUpdateCodec = PacketCodec.<RegistryByteBuf, InvestmentUpdatePayload>of(
            (payload, buf) -> {
                buf.writeInt(payload.levels().size());
                payload.levels().forEach((id, level) -> {
                    buf.writeString(id);
                    buf.writeInt(level);
                });
            },
            buf -> {
                int size = buf.readInt();
                Map<String, Integer> data = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    data.put(buf.readString(), buf.readInt());
                }
                return new InvestmentUpdatePayload(data);
            }
        );

        PacketCodec<RegistryByteBuf, InvestmentActionPayload> investActionCodec = PacketCodec.<RegistryByteBuf, InvestmentActionPayload>of(
            (payload, buf) -> buf.writeString(payload.typeId()),
            buf -> new InvestmentActionPayload(buf.readString())
        );

        PacketCodec<RegistryByteBuf, CryptoUpdatePayload> cryptoUpdateCodec = PacketCodec.<RegistryByteBuf, CryptoUpdatePayload>of(
            (payload, buf) -> {
                buf.writeInt(payload.prices().size());
                payload.prices().forEach((id, price) -> {
                    buf.writeString(id);
                    buf.writeDouble(price);
                });
            },
            buf -> {
                int size = buf.readInt();
                Map<String, Double> data = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    data.put(buf.readString(), buf.readDouble());
                }
                return new CryptoUpdatePayload(data);
            }
        );

        PacketCodec<RegistryByteBuf, CryptoActionPayload> cryptoActionCodec = PacketCodec.<RegistryByteBuf, CryptoActionPayload>of(
            (payload, buf) -> {
                buf.writeString(payload.action());
                buf.writeString(payload.coinId());
                buf.writeDouble(payload.amount());
            },
            buf -> new CryptoActionPayload(buf.readString(), buf.readString(), buf.readDouble())
        );

        PayloadTypeRegistry.playS2C().register(BalanceUpdatePayload.ID, balanceCodecCorrect);
        PayloadTypeRegistry.playS2C().register(MarketUpdatePayload.ID, marketCodec);
        PayloadTypeRegistry.playS2C().register(PortfolioUpdatePayload.ID, portfolioCodec);
        PayloadTypeRegistry.playS2C().register(InvestmentUpdatePayload.ID, investUpdateCodec);
        PayloadTypeRegistry.playS2C().register(CryptoUpdatePayload.ID, cryptoUpdateCodec);
        PayloadTypeRegistry.playC2S().register(PurchasePayload.ID, purchaseCodec);
        PayloadTypeRegistry.playC2S().register(InvestmentActionPayload.ID, investActionCodec);
        PayloadTypeRegistry.playC2S().register(CryptoActionPayload.ID, cryptoActionCodec);
    }

    public static void sendBalanceUpdate(ServerPlayerEntity player, double balance) {
        ServerPlayNetworking.send(player, new BalanceUpdatePayload(balance));
    }

    public static void sendMarketUpdate(net.minecraft.server.MinecraftServer server, Map<String, MarketManager.MarketItem> data) {
        server.getPlayerManager().getPlayerList().forEach(player -> 
            ServerPlayNetworking.send(player, new MarketUpdatePayload(data))
        );
    }

    public static void sendPortfolioUpdate(ServerPlayerEntity player, Map<String, Double> avgPrices) {
        ServerPlayNetworking.send(player, new PortfolioUpdatePayload(avgPrices));
    }

    public static void sendInvestmentUpdate(ServerPlayerEntity player, Map<String, Integer> levels) {
        ServerPlayNetworking.send(player, new InvestmentUpdatePayload(levels));
    }

    public static void sendCryptoUpdate(net.minecraft.server.MinecraftServer server, Map<String, Double> prices) {
        server.getPlayerManager().getPlayerList().forEach(player -> 
            ServerPlayNetworking.send(player, new CryptoUpdatePayload(prices))
        );
    }
}
