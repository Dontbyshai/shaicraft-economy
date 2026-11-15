/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.economy.EconomyManager;
import fr.shai.shaicrafteconomy.economy.EconomyRules;
import fr.shai.shaicrafteconomy.economy.ModConfig;
import fr.shai.shaicrafteconomy.economy.TransactionLog;
import fr.shai.shaicrafteconomy.gui.TradingGui;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class EconomyCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("balance")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                double balance = ShaiCraftEconomy.getInstance().getEconomyManager().getBalance(player.getUuid());
                context.getSource().sendFeedback(() -> Text.translatable("msg.shaicraft-economy.balance", String.format("%.2f", balance)), false);
                return 1;
            })
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                    double currentBalance = ShaiCraftEconomy.getInstance().getEconomyManager().getBalance(player.getUuid());
                    context.getSource().sendFeedback(() -> Text.translatable("msg.shaicraft-economy.balance_other", player.getName().getString(), String.format("%.2f", currentBalance)), false);
                    return 1;
                })));

        dispatcher.register(CommandManager.literal("pay")
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(context -> pay(context.getSource(), EntityArgumentType.getPlayer(context, "player"), DoubleArgumentType.getDouble(context, "amount"))))));

        dispatcher.register(CommandManager.literal("shop")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) player.openHandledScreen(new fr.shai.shaicrafteconomy.gui.EconomyShopGui());
                return 1;
            }));

        dispatcher.register(CommandManager.literal("trading")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) player.openHandledScreen(new TradingGui());
                return 1;
            })
            .then(CommandManager.literal("margin")
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0))
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        double amount = DoubleArgumentType.getDouble(context, "amount");
                        if (amount == 0) {
                            TradingGui.clearCustomMargin(player.getUuid());
                            player.sendMessage(Text.translatable("msg.shaicraft-economy.margin_cleared").formatted(Formatting.GREEN));
                            return 1;
                        }
                        if (!EconomyRules.isPositiveMoney(amount)) {
                            context.getSource().sendError(Text.translatable("msg.shaicraft-economy.invalid_amount"));
                            return 0;
                        }
                        TradingGui.setCustomMargin(player.getUuid(), amount);
                        player.sendMessage(Text.translatable("msg.shaicraft-economy.margin_set", String.format("%.2f", amount)).formatted(Formatting.GREEN));
                        return 1;
                    }))));

        dispatcher.register(CommandManager.literal("hdv")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) player.openHandledScreen(new fr.shai.shaicrafteconomy.gui.PokemonAuctionGui());
                return 1;
            })
            .then(CommandManager.literal("sell")
                .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                    .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(1.0))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            int slot = IntegerArgumentType.getInteger(context, "slot") - 1;
                            double price = DoubleArgumentType.getDouble(context, "price");
                            if (player != null) {
                                fr.shai.shaicrafteconomy.gui.PokemonAuctionGui.listPokemon(player, slot, price);
                            }
                            return 1;
                        })))));

        dispatcher.register(CommandManager.literal("trade")
            .then(CommandManager.argument("target", EntityArgumentType.player())
                .executes(context -> {
                    ServerPlayerEntity sender = context.getSource().getPlayer();
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                    if (sender != null) {
                        ShaiCraftEconomy.getInstance().getTradeSessionManager().sendRequest(sender, target);
                    }
                    return 1;
                }))
            .then(CommandManager.literal("accept")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity accepter = context.getSource().getPlayer();
                        ServerPlayerEntity sender = EntityArgumentType.getPlayer(context, "target");
                        if (accepter != null) {
                            ShaiCraftEconomy.getInstance().getTradeSessionManager().acceptRequest(accepter, sender);
                        }
                        return 1;
                    }))));

        dispatcher.register(CommandManager.literal("historique")
            .executes(context -> showHistory(context.getSource(), context.getSource().getPlayer()))
            .then(CommandManager.argument("joueur", EntityArgumentType.player())
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> showHistory(context.getSource(), EntityArgumentType.getPlayer(context, "joueur")))));

        dispatcher.register(CommandManager.literal("baltop")
            .executes(context -> showTop(context.getSource())));

        dispatcher.register(CommandManager.literal("eco")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                context.getSource().sendFeedback(() -> Text.translatable("msg.shaicraft-economy.eco_usage"), false);
                return 1;
            })
            .then(CommandManager.literal("give")
                .then(CommandManager.argument("cible", GameProfileArgumentType.gameProfile())
                    .then(CommandManager.argument("montant", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adjust(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "cible"), DoubleArgumentType.getDouble(context, "montant"), "give")))))
            .then(CommandManager.literal("set")
                .then(CommandManager.argument("cible", GameProfileArgumentType.gameProfile())
                    .then(CommandManager.argument("montant", DoubleArgumentType.doubleArg(0.0))
                        .executes(context -> adjust(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "cible"), DoubleArgumentType.getDouble(context, "montant"), "set")))))
            .then(CommandManager.literal("take")
                .then(CommandManager.argument("cible", GameProfileArgumentType.gameProfile())
                    .then(CommandManager.argument("montant", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> adjust(context.getSource(), GameProfileArgumentType.getProfileArgument(context, "cible"), DoubleArgumentType.getDouble(context, "montant"), "take")))))
            .then(CommandManager.literal("reload")
                .executes(context -> {
                    ModConfig.load();
                    ModConfig config = ModConfig.get();
                    context.getSource().sendFeedback(() -> Text.translatable("msg.shaicraft-economy.eco_reload",
                        String.format("%.4f", config.usdToEur),
                        String.format("%.2f", config.payTaxPercent),
                        config.autosaveSeconds), true);
                    return 1;
                })));
    }

    private static int pay(ServerCommandSource source, ServerPlayerEntity target, double amount) {
        ServerPlayerEntity sender = source.getPlayer();
        if (sender == null) return 0;
        if (!EconomyRules.isPositiveMoney(amount)) {
            source.sendError(Text.translatable("msg.shaicraft-economy.invalid_amount"));
            return 0;
        }
        if (sender.getUuid().equals(target.getUuid())) {
            source.sendError(Text.translatable("msg.shaicraft-economy.cannot_pay_self"));
            return 0;
        }

        double taxPercent = ModConfig.get().payTaxPercent;
        double tax = EconomyRules.tax(amount, taxPercent);
        double received = EconomyRules.netAfterTax(amount, taxPercent);
        if (received <= 0) {
            source.sendError(Text.translatable("msg.shaicraft-economy.invalid_amount"));
            return 0;
        }

        EconomyManager econ = ShaiCraftEconomy.getInstance().getEconomyManager();
        if (!econ.removeBalance(sender.getUuid(), amount)) {
            source.sendError(Text.translatable("msg.shaicraft-economy.insufficient_funds"));
            return 0;
        }
        econ.addBalance(target.getUuid(), received);
        econ.rememberName(sender.getUuid(), sender.getGameProfile().getName());
        econ.rememberName(target.getUuid(), target.getGameProfile().getName());

        TransactionLog log = ShaiCraftEconomy.getInstance().getTransactionLog();
        log.add(sender.getUuid(), "PAY_SENT", -amount, target.getName().getString());
        log.add(target.getUuid(), "PAY_RECEIVED", received, sender.getName().getString());

        ShaiCraftEconomy.getInstance().syncBalance(sender);
        ShaiCraftEconomy.getInstance().syncBalance(target);

        if (tax > 0) {
            sender.sendMessage(Text.translatable("msg.shaicraft-economy.pay_success_tax",
                String.format("%.2f", amount), target.getName().getString(), String.format("%.2f", tax), String.format("%.2f", received)));
            target.sendMessage(Text.translatable("msg.shaicraft-economy.pay_receive_tax",
                String.format("%.2f", received), sender.getName().getString(), String.format("%.2f", tax)));
        } else {
            sender.sendMessage(Text.translatable("msg.shaicraft-economy.pay_success", String.format("%.2f", amount), target.getName().getString()));
            target.sendMessage(Text.translatable("msg.shaicraft-economy.pay_receive", String.format("%.2f", received), sender.getName().getString()));
        }
        return 1;
    }

    private static int showHistory(ServerCommandSource source, ServerPlayerEntity target) {
        if (target == null) {
            source.sendError(Text.translatable("msg.shaicraft-economy.history_need_player"));
            return 0;
        }
        var list = ShaiCraftEconomy.getInstance().getTransactionLog().latest(target.getUuid(), 10);
        source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.history_header", target.getName().getString()), false);
        if (list.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.history_empty"), false);
            return 1;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            TransactionLog.Entry entry = list.get(i);
            String when = TransactionLog.formatTime(entry.time);
            String money = String.format("%+.2f", entry.amount);
            String detail = entry.detail == null ? "" : entry.detail;
            source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.history_line",
                when, Text.translatable("msg.shaicraft-economy.tx." + entry.type), money, detail), false);
        }
        return 1;
    }

    private static int showTop(ServerCommandSource source) {
        EconomyManager econ = ShaiCraftEconomy.getInstance().getEconomyManager();
        Map<UUID, Double> all = econ.getAllBalances();
        var top = EconomyRules.topBalances(all, 10);
        source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.baltop_header"), false);
        if (top.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.baltop_empty"), false);
            return 1;
        }
        int index = 1;
        for (var entry : top) {
            int rank = index++;
            String name = econ.getName(entry.getKey());
            String money = String.format("%.2f", entry.getValue());
            source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.baltop_line", rank, name, money), false);
        }
        ServerPlayerEntity self = source.getPlayer();
        if (self != null) {
            int myRank = EconomyRules.rankOf(all, self.getUuid());
            String mine = String.format("%.2f", econ.getBalance(self.getUuid()));
            source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.baltop_you", myRank, mine), false);
        }
        return 1;
    }

    private static int adjust(ServerCommandSource source, Collection<GameProfile> profiles, double amount, String mode) {
        boolean setMode = "set".equals(mode);
        if (setMode ? !EconomyRules.isNonNegativeMoney(amount) : !EconomyRules.isPositiveMoney(amount)) {
            source.sendError(Text.translatable("msg.shaicraft-economy.invalid_amount"));
            return 0;
        }
        EconomyManager econ = ShaiCraftEconomy.getInstance().getEconomyManager();
        TransactionLog log = ShaiCraftEconomy.getInstance().getTransactionLog();
        int changed = 0;
        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            econ.rememberName(uuid, profile.getName());
            double before = econ.getBalance(uuid);
            if ("give".equals(mode)) {
                econ.addBalance(uuid, amount);
            } else if ("set".equals(mode)) {
                econ.setBalance(uuid, amount);
            } else if (!econ.removeBalance(uuid, amount)) {
                source.sendError(Text.translatable("msg.shaicraft-economy.eco_take_fail", profile.getName()));
                continue;
            }
            double after = econ.getBalance(uuid);
            log.add(uuid, "ADMIN", after - before, mode);
            ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(uuid);
            if (online != null) {
                ShaiCraftEconomy.getInstance().syncBalance(online);
                online.sendMessage(Text.translatable("msg.shaicraft-economy.eco_received", String.format("%.2f", after)).formatted(Formatting.GOLD));
            }
            String shown = String.format("%.2f", after);
            String given = String.format("%.2f", amount);
            String name = profile.getName();
            if ("give".equals(mode)) {
                source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.eco_give", given, name, shown), true);
            } else if ("set".equals(mode)) {
                source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.eco_set", name, shown), true);
            } else {
                source.sendFeedback(() -> Text.translatable("msg.shaicraft-economy.eco_take", given, name, shown), true);
            }
            changed++;
        }
        if (changed > 0) ShaiCraftEconomy.getInstance().savePersistentData();
        return changed;
    }
}
