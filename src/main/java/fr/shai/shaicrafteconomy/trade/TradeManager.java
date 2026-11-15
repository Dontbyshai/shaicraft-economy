/**
 * ShaiCraft Economy - Player-to-Player Trading Manager
 */
package fr.shai.shaicrafteconomy.trade;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.economy.EconomyManager;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeManager {
    // pending requests: targetUUID -> senderUUID
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    
    // active sessions: playerUUID -> TradeSession
    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();
    
    // waiting for chat input: playerUUID -> TradeSession
    private final Map<UUID, TradeSession> awaitingMoneyInput = new HashMap<>();

    public static class TradeSession {
        public ServerPlayerEntity player1;
        public ServerPlayerEntity player2;
        public SimpleInventory inventory;
        public double p1Money = 0.0;
        public double p2Money = 0.0;
        public boolean p1Ready = false;
        public boolean p2Ready = false;

        public TradeSession(ServerPlayerEntity p1, ServerPlayerEntity p2) {
            this.player1 = p1;
            this.player2 = p2;
            this.inventory = new SimpleInventory(54);
        }

        public boolean isPlayer1(UUID uuid) {
            return player1.getUuid().equals(uuid);
        }
        
        public ServerPlayerEntity getOther(UUID uuid) {
            return isPlayer1(uuid) ? player2 : player1;
        }

        public void cancel() {
            // Return items to players
            returnItems(player1, true);
            returnItems(player2, false);
            player1.sendMessage(Text.translatable("msg.shaicraft-economy.trade_cancelled").formatted(Formatting.RED));
            player2.sendMessage(Text.translatable("msg.shaicraft-economy.trade_cancelled").formatted(Formatting.RED));
            closeGUIs();
        }

        private void returnItems(ServerPlayerEntity p, boolean isP1) {
            int[] cols = isP1 ? new int[]{0, 1, 2, 3} : new int[]{5, 6, 7, 8};
            for (int row = 0; row < 6; row++) {
                for (int col : cols) {
                    ItemStack stack = inventory.getStack(row * 9 + col);
                    if (!stack.isEmpty()) {
                        if (!p.getInventory().insertStack(stack)) {
                            p.dropItem(stack, false);
                        }
                    }
                }
            }
        }

        public void execute() {
            EconomyManager eco = ShaiCraftEconomy.getInstance().getEconomyManager();
            
            // Check balances
            if (eco.getBalance(player1.getUuid()) < p1Money) {
                cancel();
                player1.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
                return;
            }
            if (eco.getBalance(player2.getUuid()) < p2Money) {
                cancel();
                player2.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
                return;
            }

            // Transfer money
            eco.removeBalance(player1.getUuid(), p1Money);
            eco.addBalance(player2.getUuid(), p1Money);
            
            eco.removeBalance(player2.getUuid(), p2Money);
            eco.addBalance(player1.getUuid(), p2Money);

            var history = ShaiCraftEconomy.getInstance().getTransactionLog();
            if (p1Money > 0) {
                history.add(player1.getUuid(), "TRADE_SENT", -p1Money, player2.getName().getString());
                history.add(player2.getUuid(), "TRADE_RECEIVED", p1Money, player1.getName().getString());
            }
            if (p2Money > 0) {
                history.add(player2.getUuid(), "TRADE_SENT", -p2Money, player1.getName().getString());
                history.add(player1.getUuid(), "TRADE_RECEIVED", p2Money, player2.getName().getString());
            }
            
            ShaiCraftEconomy.getInstance().syncBalance(player1);
            ShaiCraftEconomy.getInstance().syncBalance(player2);

            // Transfer items
            giveItems(player1, false); // P1 gets P2's items
            giveItems(player2, true);  // P2 gets P1's items

            player1.sendMessage(Text.translatable("msg.shaicraft-economy.trade_success").formatted(Formatting.GREEN));
            player2.sendMessage(Text.translatable("msg.shaicraft-economy.trade_success").formatted(Formatting.GREEN));
            closeGUIs();
        }

        private void giveItems(ServerPlayerEntity p, boolean fromP1Area) {
            int[] cols = fromP1Area ? new int[]{0, 1, 2, 3} : new int[]{5, 6, 7, 8};
            for (int row = 0; row < 6; row++) {
                for (int col : cols) {
                    ItemStack stack = inventory.getStack(row * 9 + col);
                    if (!stack.isEmpty()) {
                        if (!p.getInventory().insertStack(stack)) {
                            p.dropItem(stack, false);
                        }
                    }
                }
            }
        }

        private void closeGUIs() {
            player1.closeHandledScreen();
            player2.closeHandledScreen();
        }
    }

    public void sendRequest(ServerPlayerEntity sender, ServerPlayerEntity target) {
        if (sender.getUuid().equals(target.getUuid())) {
            sender.sendMessage(Text.translatable("msg.shaicraft-economy.trade_self").formatted(Formatting.RED));
            return;
        }
        if (activeSessions.containsKey(sender.getUuid()) || activeSessions.containsKey(target.getUuid())) {
            sender.sendMessage(Text.translatable("msg.shaicraft-economy.trade_busy").formatted(Formatting.RED));
            return;
        }

        pendingRequests.put(target.getUuid(), sender.getUuid());
        sender.sendMessage(Text.translatable("msg.shaicraft-economy.trade_sent", target.getName().getString()).formatted(Formatting.GREEN));

        Text acceptBtn = Text.translatable("msg.shaicraft-economy.trade_accept")
            .formatted(Formatting.GREEN, Formatting.BOLD)
            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept " + sender.getName().getString())));

        target.sendMessage(Text.translatable("msg.shaicraft-economy.trade_request", sender.getName().getString()).formatted(Formatting.YELLOW).append(acceptBtn));
    }

    public void acceptRequest(ServerPlayerEntity target, ServerPlayerEntity sender) {
        UUID expectedSender = pendingRequests.get(target.getUuid());
        if (expectedSender == null || !expectedSender.equals(sender.getUuid())) {
            target.sendMessage(Text.translatable("msg.shaicraft-economy.trade_no_request").formatted(Formatting.RED));
            return;
        }
        
        pendingRequests.remove(target.getUuid());
        
        TradeSession session = new TradeSession(sender, target);
        activeSessions.put(sender.getUuid(), session);
        activeSessions.put(target.getUuid(), session);

        openGuiFor(sender);
        openGuiFor(target);
    }

    public void openGuiFor(ServerPlayerEntity player) {
        TradeSession session = activeSessions.get(player.getUuid());
        if (session != null) {
            player.openHandledScreen(new fr.shai.shaicrafteconomy.gui.PlayerTradeGui(session, player));
        }
    }

    public TradeSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public void endSession(TradeSession session) {
        activeSessions.remove(session.player1.getUuid());
        activeSessions.remove(session.player2.getUuid());
        awaitingMoneyInput.remove(session.player1.getUuid());
        awaitingMoneyInput.remove(session.player2.getUuid());
    }

    public void requestMoneyInput(ServerPlayerEntity player) {
        TradeSession session = activeSessions.get(player.getUuid());
        if (session != null) {
            player.closeHandledScreen();
            awaitingMoneyInput.put(player.getUuid(), session);
            player.sendMessage(Text.translatable("msg.shaicraft-economy.trade_ask_money").formatted(Formatting.GOLD, Formatting.BOLD));
            player.sendMessage(Text.translatable("msg.shaicraft-economy.trade_cancel_hint").formatted(Formatting.GRAY));
        }
    }

    public boolean handleChatInput(ServerPlayerEntity player, String message) {
        TradeSession session = awaitingMoneyInput.remove(player.getUuid());
        if (session == null) return false; // Let normal chat proceed

        if (message.trim().equalsIgnoreCase("annuler")) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.trade_input_cancelled").formatted(Formatting.YELLOW));
            openGuiFor(player);
            return true;
        }

        try {
            double amount = Double.parseDouble(message.trim());
            if (amount < 0) amount = 0;
            EconomyManager eco = ShaiCraftEconomy.getInstance().getEconomyManager();
            if (amount > eco.getBalance(player.getUuid())) {
                player.sendMessage(Text.translatable("msg.shaicraft-economy.trade_too_poor", String.format("%.2f", eco.getBalance(player.getUuid()))).formatted(Formatting.RED));
                awaitingMoneyInput.put(player.getUuid(), session); // allow retry
                return true;
            }

            if (session.isPlayer1(player.getUuid())) {
                session.p1Money = amount;
                session.p1Ready = false;
                session.p2Ready = false; // Reset readiness
            } else {
                session.p2Money = amount;
                session.p1Ready = false;
                session.p2Ready = false;
            }
            
            openGuiFor(session.player1);
            openGuiFor(session.player2);
            
        } catch (NumberFormatException e) {
            player.sendMessage(Text.translatable("msg.shaicraft-economy.trade_invalid_number").formatted(Formatting.RED));
            awaitingMoneyInput.put(player.getUuid(), session); // allow retry
        }
        return true; // Input handled, hide from chat
    }
}
