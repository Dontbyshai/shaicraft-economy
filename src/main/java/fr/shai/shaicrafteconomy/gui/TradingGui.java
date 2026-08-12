/**
 * ShaiCraft Economy - Leveraged Trading Interface
 * Visual chart (ASCII bar chart using colored glass panes) + Long/Short with leverage
 */
package fr.shai.shaicrafteconomy.gui;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.economy.EconomyManager;
import fr.shai.shaicrafteconomy.market.LeverageTradeManager;
import fr.shai.shaicrafteconomy.market.LeverageTradeManager.Direction;
import fr.shai.shaicrafteconomy.market.LeverageTradeManager.Position;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class TradingGui implements NamedScreenHandlerFactory {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final String[] COINS      = {"bitcoin", "ethereum", "solana", "dogecoin"};
    private static final String[] COIN_NAMES = {"Bitcoin (BTC)", "Ethereum (ETH)", "Solana (SOL)", "Dogecoin (DOGE)"};
    private static final int[]    LEVERAGES  = {1, 2, 5, 10, 25, 50, 100};
    private static final double[] AMOUNTS    = {100, 500, 1000, 5000, 10000};

    // ── State ─────────────────────────────────────────────────────────────────
    private int     coinIndex      = 0;   // selected coin
    private int     leverageIndex  = 2;   // default x5
    private int     amountIndex    = 1;   // default 500€
    private boolean showPositions  = false;
    private static final Map<UUID, Double> customMargins = new HashMap<>();

    public static void setCustomMargin(UUID playerUuid, double margin) {
        customMargins.put(playerUuid, margin);
    }

    // ── Chart layout: rows 0–4 × cols 0–8 (45 slots) for chart ──────────────
    // Row 5 (slots 45–53): controls
    private static final int CHART_ROWS = 5;
    private static final int CHART_COLS = 9;

    @Override
    public Text getDisplayName() {
        return Text.literal("📈 ShaiCraft Trading – " + COIN_NAMES[coinIndex])
            .formatted(Formatting.BOLD, Formatting.GOLD);
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(54);
        buildGui(inv, (ServerPlayerEntity) player);

        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inv, 6) {
            @Override
            public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
                if (slot < 0 || slot >= 54) { super.onSlotClick(slot, button, action, player); return; }
                ServerPlayerEntity sp = (ServerPlayerEntity) player;
                handleClick(sp, slot, button, action, inv, this);
            }
            @Override public boolean canUse(PlayerEntity p) { return true; }
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    private void buildGui(SimpleInventory inv, ServerPlayerEntity player) {
        // Clear
        for (int i = 0; i < 54; i++) inv.setStack(i, ItemStack.EMPTY);

        if (showPositions) {
            buildPositionsView(inv, player);
        } else {
            buildChartView(inv, player);
        }
    }

    // ── CHART VIEW ────────────────────────────────────────────────────────────
    private void buildChartView(SimpleInventory inv, ServerPlayerEntity player) {
        String  coin     = COINS[coinIndex];
        double  curPrice = ShaiCraftEconomy.getInstance().getCryptoManager().getPrice(coin);
        List<Double> hist = LeverageTradeManager.getHistory(coin);

        // ── Draw chart (rows 0–4, cols 0–8) ──────────────────────────────────
        drawChart(inv, hist, curPrice);

        // ── Row 5: controls ─────────────────────────────────────────────────
        // Slot 45 – Prev coin
        inv.setStack(45, btn(Items.ARROW, "◀ Cryptomonnaie", Formatting.YELLOW,
            List.of(Text.literal("Précédent").formatted(Formatting.GRAY))));

        // Slot 46 – Coin info
        inv.setStack(46, buildCoinInfo(coin, curPrice, player));

        // Slot 47 – Next coin
        inv.setStack(47, btn(Items.ARROW, "Cryptomonnaie ▶", Formatting.YELLOW,
            List.of(Text.literal("Suivant").formatted(Formatting.GRAY))));

        // Slot 48 – Separator
        inv.setStack(48, glass(Items.GRAY_STAINED_GLASS_PANE));

        // Slot 49 – Leverage selector
        inv.setStack(49, buildLeverageBtn());

        // Slot 50 – Separator
        inv.setStack(50, glass(Items.GRAY_STAINED_GLASS_PANE));

        // Slot 51 – Amount selector
        inv.setStack(51, buildAmountBtn(player));

        // Slot 52 – LONG button
        inv.setStack(52, buildOrderBtn(true, curPrice, player));

        // Slot 53 – SHORT button
        inv.setStack(53, buildOrderBtn(false, curPrice, player));

        // Slot 44 – Toggle positions view
        inv.setStack(44, btn(Items.BOOK, "📋 Mes positions (" +
            ShaiCraftEconomy.getInstance().getLeverageTradeManager()
                .getPositions(player.getUuid().toString()).size() + ")",
            Formatting.GOLD, List.of(Text.literal("Cliquez pour voir vos positions").formatted(Formatting.GRAY))));
    }

    // ── Draw price chart using colored glass panes ────────────────────────────
    private void drawChart(SimpleInventory inv, List<Double> hist, double curPrice) {
        if (hist.size() < 2) {
            // No history - show "loading" placeholders
            for (int row = 0; row < CHART_ROWS; row++) {
                for (int col = 0; col < CHART_COLS; col++) {
                    ItemStack g = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
                    g.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Chargement des données...").formatted(Formatting.GRAY));
                    inv.setStack(row * 9 + col, g);
                }
            }
            // Center message
            ItemStack msg = new ItemStack(Items.PAPER);
            msg.set(DataComponentTypes.CUSTOM_NAME, Text.literal("📡 En attente de données...").formatted(Formatting.YELLOW, Formatting.BOLD));
            msg.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Les prix se mettent à jour toutes les 5s").formatted(Formatting.GRAY),
                Text.literal("Revenez dans un instant !").formatted(Formatting.GRAY)
            )));
            inv.setStack(22, msg);
            return;
        }

        // We draw the last CHART_COLS price points
        int colCount = Math.min(CHART_COLS, hist.size());
        List<Double> visible = hist.subList(hist.size() - colCount, hist.size());

        double minPrice = visible.stream().mapToDouble(Double::doubleValue).min().orElse(curPrice * 0.99);
        double maxPrice = visible.stream().mapToDouble(Double::doubleValue).max().orElse(curPrice * 1.01);
        double range    = maxPrice - minPrice;
        if (range < 0.01) range = 0.01;

        for (int col = 0; col < CHART_COLS; col++) {
            double price;
            if (col < colCount) {
                price = visible.get(col);
            } else {
                // Empty columns on the left if not enough data
                for (int row = 0; row < CHART_ROWS; row++) inv.setStack(row * 9 + col, glass(Items.BLACK_STAINED_GLASS_PANE));
                continue;
            }

            // Determine if price went up or down vs previous
            boolean up = (col == 0) || price >= visible.get(col - 1);
            Item glassColor = up ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
            Item darkColor  = up ? Items.GREEN_STAINED_GLASS_PANE : Items.ORANGE_STAINED_GLASS_PANE;

            // Normalized height: 0 = bottom, CHART_ROWS-1 = top
            double normalized = (price - minPrice) / range; // 0.0–1.0
            int filledRows = (int) Math.round(normalized * (CHART_ROWS - 1)) + 1; // 1–CHART_ROWS

            for (int row = 0; row < CHART_ROWS; row++) {
                // In Minecraft inventory, row 0 = top, row 4 = bottom
                int invRow = CHART_ROWS - 1 - row;
                int slot = invRow * 9 + col;
                if (row < filledRows) {
                    // Filled bar
                    Item stackItem = (row == filledRows - 1) ? glassColor : darkColor;
                    ItemStack g = new ItemStack(stackItem);
                    g.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal(String.format("%,.2f€", price)).formatted(
                            up ? Formatting.GREEN : Formatting.RED));
                    inv.setStack(slot, g);
                } else {
                    // Empty space above bar
                    inv.setStack(slot, glass(Items.BLACK_STAINED_GLASS_PANE));
                }
            }
        }
    }

    // ── POSITIONS VIEW ────────────────────────────────────────────────────────
    private void buildPositionsView(SimpleInventory inv, ServerPlayerEntity player) {
        LeverageTradeManager mgr = ShaiCraftEconomy.getInstance().getLeverageTradeManager();
        List<Position> positions = mgr.getPositions(player.getUuid().toString());

        double totalPnl = 0;

        for (int i = 0; i < Math.min(positions.size(), 45); i++) {
            Position pos = positions.get(i);
            double curPrice = ShaiCraftEconomy.getInstance().getCryptoManager().getPrice(pos.coinId);
            double pnl = pos.unrealizedPnl(curPrice);
            totalPnl += pnl;

            boolean isProfit = pnl >= 0;
            Item icon = isProfit ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
            String coinName = coinNameFor(pos.coinId);

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Crypto: " + coinName).formatted(Formatting.WHITE));
            lore.add(Text.literal("Direction: ").formatted(Formatting.GRAY)
                .append(Text.literal(pos.direction == Direction.LONG ? "▲ LONG" : "▼ SHORT")
                    .formatted(pos.direction == Direction.LONG ? Formatting.GREEN : Formatting.RED)));
            lore.add(Text.literal("Levier: ").formatted(Formatting.GRAY)
                .append(Text.literal("x" + (int)pos.leverage).formatted(Formatting.GOLD)));
            lore.add(Text.literal("Marge: ").formatted(Formatting.GRAY)
                .append(Text.literal(fmt(pos.margin) + "€").formatted(Formatting.YELLOW)));
            lore.add(Text.literal("Taille: ").formatted(Formatting.GRAY)
                .append(Text.literal(fmt(pos.size) + "€").formatted(Formatting.WHITE)));
            lore.add(Text.literal("Prix entrée: ").formatted(Formatting.GRAY)
                .append(Text.literal(fmt(pos.entryPrice) + "€").formatted(Formatting.WHITE)));
            lore.add(Text.literal("Prix actuel: ").formatted(Formatting.GRAY)
                .append(Text.literal(fmt(curPrice) + "€").formatted(Formatting.AQUA)));
            lore.add(Text.literal("Liquidation à: ").formatted(Formatting.GRAY)
                .append(Text.literal(fmt(pos.liquidationPrice()) + "€").formatted(Formatting.DARK_RED)));
            lore.add(Text.empty());
            lore.add(Text.literal("P&L: ").formatted(Formatting.GRAY)
                .append(Text.literal((pnl >= 0 ? "+" : "") + fmt(pnl) + "€")
                    .formatted(isProfit ? Formatting.GREEN : Formatting.RED)));
            lore.add(Text.empty());
            lore.add(Text.literal("Clic gauche = Fermer position").formatted(Formatting.YELLOW));

            ItemStack stack = new ItemStack(icon);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal((pos.direction == Direction.LONG ? "▲ LONG " : "▼ SHORT ") + coinName + " x" + (int)pos.leverage)
                    .formatted(Formatting.BOLD, pos.direction == Direction.LONG ? Formatting.GREEN : Formatting.RED));
            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(i, stack);
        }

        if (positions.isEmpty()) {
            ItemStack empty = new ItemStack(Items.BARRIER);
            empty.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Aucune position ouverte").formatted(Formatting.RED));
            empty.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Ouvrez un Long ou Short depuis le graphique").formatted(Formatting.GRAY)
            )));
            inv.setStack(22, empty);
        }

        // Total PnL display (slot 49)
        boolean totalPos = totalPnl >= 0;
        ItemStack pnlDisplay = new ItemStack(totalPos ? Items.GOLD_INGOT : Items.IRON_NUGGET);
        pnlDisplay.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("P&L Total: " + (totalPos ? "+" : "") + fmt(totalPnl) + "€")
                .formatted(Formatting.BOLD, totalPos ? Formatting.GREEN : Formatting.RED));
        inv.setStack(49, pnlDisplay);

        // Back button (slot 45)
        inv.setStack(45, btn(Items.ARROW, "◀ Retour au graphique", Formatting.YELLOW, List.of()));
    }

    // ── Click handler ─────────────────────────────────────────────────────────
    private void handleClick(ServerPlayerEntity sp, int slot, int button, SlotActionType action,
                             SimpleInventory inv, GenericContainerScreenHandler handler) {
        String uuid = sp.getUuid().toString();
        LeverageTradeManager mgr = ShaiCraftEconomy.getInstance().getLeverageTradeManager();
        EconomyManager eco = ShaiCraftEconomy.getInstance().getEconomyManager();

        if (showPositions) {
            if (slot == 45) { showPositions = false; reopen(sp); return; }
            // Close a position
            List<Position> positions = mgr.getPositions(uuid);
            if (slot < positions.size()) {
                Position pos = positions.get(slot);
                double curPrice = ShaiCraftEconomy.getInstance().getCryptoManager().getPrice(pos.coinId);
                double pnl = pos.unrealizedPnl(curPrice);
                double returned = pos.margin + pnl;
                if (returned < 0) returned = 0;
                mgr.closePosition(uuid, slot);
                eco.addBalance(sp.getUuid(), returned);
                ShaiCraftEconomy.getInstance().syncBalance(sp);
                sp.sendMessage(Text.literal(
                    "✖ Position fermée | P&L: " + (pnl >= 0 ? "+" : "") + fmt(pnl) + "€ | Reçu: " + fmt(returned) + "€"
                ).formatted(pnl >= 0 ? Formatting.GREEN : Formatting.RED));
                reopen(sp);
            }
            return;
        }

        // Chart view clicks
        switch (slot) {
            case 44 -> { showPositions = true; reopen(sp); }
            case 45 -> { coinIndex = (coinIndex - 1 + COINS.length) % COINS.length; reopen(sp); }
            case 47 -> { coinIndex = (coinIndex + 1) % COINS.length; reopen(sp); }
            case 49 -> {
                // Cycle leverage: left = up, right = down
                if (button == 0) leverageIndex = (leverageIndex + 1) % LEVERAGES.length;
                else             leverageIndex = (leverageIndex - 1 + LEVERAGES.length) % LEVERAGES.length;
                reopen(sp);
            }
            case 51 -> {
                // Cycle amount
                if (button == 0) amountIndex = (amountIndex + 1) % AMOUNTS.length;
                else             amountIndex = (amountIndex - 1 + AMOUNTS.length) % AMOUNTS.length;
                reopen(sp);
            }
            case 52 -> openTrade(sp, true,  eco, mgr);   // LONG
            case 53 -> openTrade(sp, false, eco, mgr);   // SHORT
        }
    }

    private void openTrade(ServerPlayerEntity sp, boolean isLong, EconomyManager eco, LeverageTradeManager mgr) {
        double custom = customMargins.getOrDefault(sp.getUuid(), -1.0);
        double margin   = (custom > 0) ? custom : AMOUNTS[amountIndex];
        double leverage = LEVERAGES[leverageIndex];
        String coin     = COINS[coinIndex];
        double price    = ShaiCraftEconomy.getInstance().getCryptoManager().getPrice(coin);

        if (price <= 0) {
            sp.sendMessage(Text.literal("Prix indisponible, réessayez dans un instant.").formatted(Formatting.RED));
            return;
        }

        if (!eco.removeBalance(sp.getUuid(), margin)) {
            sp.sendMessage(Text.literal("Fonds insuffisants ! Il vous faut " + fmt(margin) + "€ de marge.").formatted(Formatting.RED));
            return;
        }

        Position pos = new Position(coin, isLong ? Direction.LONG : Direction.SHORT, margin, leverage, price);
        mgr.openPosition(sp.getUuid().toString(), pos);
        ShaiCraftEconomy.getInstance().syncBalance(sp);

        sp.sendMessage(Text.literal(
            (isLong ? "▲ LONG " : "▼ SHORT ") + coinNameFor(coin) + " x" + (int)leverage +
            " | Marge: " + fmt(margin) + "€ | Taille: " + fmt(margin * leverage) + "€ | Entrée: " + fmt(price) + "€" +
            " | Liq: " + fmt(pos.liquidationPrice()) + "€"
        ).formatted(isLong ? Formatting.GREEN : Formatting.RED));
        reopen(sp);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────
    private ItemStack buildCoinInfo(String coin, double curPrice, ServerPlayerEntity player) {
        List<Double> hist = LeverageTradeManager.getHistory(coin);
        double prevPrice  = hist.size() >= 2 ? hist.get(hist.size() - 2) : curPrice;
        double change     = curPrice - prevPrice;
        double changePct  = prevPrice > 0 ? (change / prevPrice) * 100 : 0;
        boolean up = change >= 0;

        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Prix actuel: ").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(curPrice) + "€").formatted(Formatting.WHITE)));
        lore.add(Text.literal("Variation: ").formatted(Formatting.GRAY)
            .append(Text.literal((up ? "+" : "") + String.format("%.2f", change) + "€" +
                " (" + (up ? "+" : "") + String.format("%.2f", changePct) + "%)")
                .formatted(up ? Formatting.GREEN : Formatting.RED)));
        lore.add(Text.literal("Votre solde: ").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(ShaiCraftEconomy.getInstance().getEconomyManager().getBalance(player.getUuid())) + "€")
                .formatted(Formatting.YELLOW)));
        lore.add(Text.empty());
        lore.add(Text.literal("Maj toutes les ~5 secondes").formatted(Formatting.DARK_GRAY));

        Item icon = up ? Items.GOLD_BLOCK : Items.REDSTONE_BLOCK;
        ItemStack s = new ItemStack(icon);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal("💱 " + COIN_NAMES[coinIndex])
            .formatted(Formatting.BOLD, Formatting.GOLD));
        s.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return s;
    }

    private ItemStack buildLeverageBtn() {
        int lev = LEVERAGES[leverageIndex];
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Levier sélectionné: ").formatted(Formatting.GRAY)
            .append(Text.literal("x" + lev).formatted(Formatting.GOLD, Formatting.BOLD)));
        lore.add(Text.empty());
        for (int l : LEVERAGES) {
            lore.add(Text.literal((l == lev ? "▶ " : "  ") + "x" + l).formatted(l == lev ? Formatting.YELLOW : Formatting.GRAY));
        }
        lore.add(Text.empty());
        lore.add(Text.literal("Clic gauche = augmenter").formatted(Formatting.AQUA));
        lore.add(Text.literal("Clic droit = diminuer").formatted(Formatting.AQUA));
        if (lev >= 10) {
            lore.add(Text.empty());
            lore.add(Text.literal("⚠ Risque élevé de liquidation !").formatted(Formatting.RED));
        }

        ItemStack s = new ItemStack(Items.EXPERIENCE_BOTTLE);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal("⚡ Levier: x" + lev).formatted(Formatting.BOLD, Formatting.GOLD));
        s.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return s;
    }

    private ItemStack buildAmountBtn(ServerPlayerEntity player) {
        double custom = customMargins.getOrDefault(player.getUuid(), -1.0);
        double amount = (custom > 0) ? custom : AMOUNTS[amountIndex];
        double balance = ShaiCraftEconomy.getInstance().getEconomyManager().getBalance(player.getUuid());
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Marge sélectionnée: ").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(amount) + "€").formatted(Formatting.YELLOW, Formatting.BOLD)));
        lore.add(Text.literal("Votre solde: ").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(balance) + "€").formatted(Formatting.WHITE)));
        lore.add(Text.empty());
        
        if (custom > 0) {
            boolean canAfford = balance >= custom;
            lore.add(Text.literal("▶ " + fmt(custom) + "€ (Personnalisé)")
                .formatted(Formatting.YELLOW));
            lore.add(Text.literal("  Supprimer marge: /trading margin 0").formatted(Formatting.GRAY));
        } else {
            for (double a : AMOUNTS) {
                boolean selected = a == amount;
                boolean canAfford = balance >= a;
                lore.add(Text.literal((selected ? "▶ " : "  ") + fmt(a) + "€")
                    .formatted(selected ? Formatting.YELLOW : (canAfford ? Formatting.GRAY : Formatting.DARK_RED)));
            }
            lore.add(Text.empty());
            lore.add(Text.literal("Clic gauche = augmenter").formatted(Formatting.AQUA));
            lore.add(Text.literal("Clic droit = diminuer").formatted(Formatting.AQUA));
            lore.add(Text.literal("Personnaliser: /trading margin <montant>").formatted(Formatting.GOLD));
        }

        ItemStack s = new ItemStack(Items.GOLD_NUGGET);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal("💶 Marge: " + fmt(amount) + "€").formatted(Formatting.BOLD, Formatting.YELLOW));
        s.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return s;
    }

    private ItemStack buildOrderBtn(boolean isLong, double curPrice, ServerPlayerEntity player) {
        double custom = customMargins.getOrDefault(player.getUuid(), -1.0);
        double margin   = (custom > 0) ? custom : AMOUNTS[amountIndex];
        double leverage = LEVERAGES[leverageIndex];
        double size     = margin * leverage;

        String dir = isLong ? "▲ LONG" : "▼ SHORT";
        Formatting color = isLong ? Formatting.GREEN : Formatting.RED;

        // Approximate liquidation price
        double liqMove = curPrice / leverage;
        double liqPrice = isLong ? curPrice - liqMove : curPrice + liqMove;

        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Crypto: " + COIN_NAMES[coinIndex]).formatted(Formatting.WHITE));
        lore.add(Text.literal("Marge: ").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(margin) + "€").formatted(Formatting.YELLOW)));
        lore.add(Text.literal("Levier: ").formatted(Formatting.GRAY)
            .append(Text.literal("x" + (int)leverage).formatted(Formatting.GOLD)));
        lore.add(Text.literal("Taille totale: ").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(size) + "€").formatted(Formatting.WHITE)));
        lore.add(Text.literal("Prix entrée: ~").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(curPrice) + "€").formatted(Formatting.WHITE)));
        lore.add(Text.literal("Liquidation: ~").formatted(Formatting.GRAY)
            .append(Text.literal(fmt(liqPrice) + "€").formatted(Formatting.DARK_RED)));
        lore.add(Text.empty());
        if (isLong)  lore.add(Text.literal("Vous gagnez si le prix monte").formatted(Formatting.GREEN));
        else         lore.add(Text.literal("Vous gagnez si le prix baisse").formatted(Formatting.RED));
        lore.add(Text.empty());
        lore.add(Text.literal("CLIQUEZ POUR OUVRIR").formatted(color, Formatting.BOLD));

        Item icon = isLong ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
        ItemStack s = new ItemStack(icon);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(dir).formatted(color, Formatting.BOLD));
        s.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return s;
    }

    private ItemStack btn(Item item, String name, Formatting color, List<Text> lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(color, Formatting.BOLD));
        if (!lore.isEmpty()) s.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return s;
    }

    private ItemStack glass(Item item) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return s;
    }

    private void reopen(ServerPlayerEntity sp) {
        sp.closeHandledScreen();
        sp.openHandledScreen(this);
    }

    private String fmt(double v) {
        if (v >= 1_000_000) return String.format("%,.0fm", v / 1_000_000);
        if (v >= 1000)      return String.format("%,.0f", v);
        return String.format("%.2f", v);
    }

    private String coinNameFor(String coinId) {
        for (int i = 0; i < COINS.length; i++) {
            if (COINS[i].equals(coinId)) return COIN_NAMES[i];
        }
        return coinId;
    }
}
