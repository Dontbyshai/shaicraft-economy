/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.economy.EconomyManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Formatting;

public class EconomyCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("balance")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    double balance = ShaiCraftEconomy.getInstance().getEconomyManager().getBalance(player.getUuid());
                    context.getSource().sendFeedback(() -> Text.translatable("msg.shaicraft-economy.balance", String.format("%.2f", balance)), false);
                }
                return 1;
            })
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(context -> {
                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                    double currentBalance = ShaiCraftEconomy.getInstance().getEconomyManager().getBalance(player.getUuid());
                    context.getSource().sendFeedback(() -> Text.translatable("msg.shaicraft-economy.balance_other", player.getName().getString(), String.format("%.2f", currentBalance)), false);
                    return 1;
                })));

        dispatcher.register(CommandManager.literal("pay")
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(context -> {
                        ServerPlayerEntity source = context.getSource().getPlayer();
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                        double amount = DoubleArgumentType.getDouble(context, "amount");

                        if (source == null) return 0;
                        if (source.getUuid().equals(target.getUuid())) {
                            context.getSource().sendError(Text.translatable("msg.shaicraft-economy.cannot_pay_self"));
                            return 0;
                        }

                        EconomyManager econ = ShaiCraftEconomy.getInstance().getEconomyManager();
                        if (econ.removeBalance(source.getUuid(), amount)) {
                            econ.addBalance(target.getUuid(), amount);
                            ShaiCraftEconomy.getInstance().syncBalance(source);
                            ShaiCraftEconomy.getInstance().syncBalance(target);

                            source.sendMessage(Text.translatable("msg.shaicraft-economy.pay_success", String.format("%.2f", amount), target.getName().getString()));
                            target.sendMessage(Text.translatable("msg.shaicraft-economy.pay_receive", String.format("%.2f", amount), source.getName().getString()));
                            return 1;
                        } else {
                            context.getSource().sendError(Text.translatable("msg.shaicraft-economy.insufficient_funds"));
                            return 0;
                        }
                    }))));

        dispatcher.register(CommandManager.literal("shop")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    player.openHandledScreen(new fr.shai.shaicrafteconomy.gui.EconomyShopGui());
                }
                return 1;
            }));

        dispatcher.register(CommandManager.literal("trading")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    player.openHandledScreen(new fr.shai.shaicrafteconomy.gui.TradingGui());
                }
                return 1;
            })
            .then(CommandManager.literal("margin")
                .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        double amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount");
                        if (player != null) {
                            fr.shai.shaicrafteconomy.gui.TradingGui.setCustomMargin(player.getUuid(), amount);
                            player.sendMessage(Text.literal("✓ Marge personnalisée définie sur " + amount + "€ pour le Trading !").formatted(Formatting.GREEN));
                        }
                        return 1;
                    }))));

        dispatcher.register(CommandManager.literal("hdv")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    player.openHandledScreen(new fr.shai.shaicrafteconomy.gui.PokemonAuctionGui());
                }
                return 1;
            })
            .then(CommandManager.literal("sell")
                .then(CommandManager.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 6))
                    .then(CommandManager.argument("price", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            int slot = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "slot") - 1; // 0-indexed
                            double price = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "price");
                            
                            if (player != null) {
                                fr.shai.shaicrafteconomy.gui.PokemonAuctionGui.listPokemon(player, slot, price);
                            }
                            return 1;
                        })))));

        dispatcher.register(CommandManager.literal("trade")
            .then(CommandManager.argument("target", net.minecraft.command.argument.EntityArgumentType.player())
                .executes(context -> {
                    ServerPlayerEntity sender = context.getSource().getPlayer();
                    ServerPlayerEntity target = net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "target");
                    if (sender != null && target != null) {
                        ShaiCraftEconomy.getInstance().getTradeSessionManager().sendRequest(sender, target);
                    }
                    return 1;
                }))
            .then(CommandManager.literal("accept")
                .then(CommandManager.argument("target", net.minecraft.command.argument.EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity accepter = context.getSource().getPlayer();
                        ServerPlayerEntity sender = net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "target");
                        if (accepter != null && sender != null) {
                            ShaiCraftEconomy.getInstance().getTradeSessionManager().acceptRequest(accepter, sender);
                        }
                        return 1;
                    }))));
    }
}

