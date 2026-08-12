/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.client.gui;

import fr.shai.shaicrafteconomy.gui.TradingScreenHandler;
import fr.shai.shaicrafteconomy.network.Networking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.HashMap;
import java.util.Map;

public class TradingScreen extends HandledScreen<TradingScreenHandler> {
    private Map<String, Double> prices = new HashMap<>();
    private Map<String, Double> averages = new HashMap<>();
    private double balance = 0.0;
    
    private String selectedCoin = "bitcoin";
    private double selectedAmount = 0.1;

    public TradingScreen(TradingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 256;
        this.backgroundHeight = 180;
    }

    public void updatePrices(Map<String, Double> prices) {
        this.prices.putAll(prices);
    }

    public void updatePortfolio(Map<String, Double> averages) {
        this.averages.putAll(averages);
    }

    public void updateBalance(double balance) {
        this.balance = balance;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Binance Dark theme
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF0B0E11); 
        
        // Header
        context.fill(x, y, x + backgroundWidth, y + 30, 0xFF1E2329);
        context.drawText(textRenderer, Text.literal("BINANCE TRADING").formatted(Formatting.BOLD, Formatting.YELLOW), x + 10, y + 10, 0xFFFFFFFF, false);
        
        String balStr = String.format("%.2f €", balance);
        context.drawText(textRenderer, Text.literal(balStr).formatted(Formatting.GREEN, Formatting.BOLD), x + backgroundWidth - 10 - textRenderer.getWidth(balStr), y + 10, 0xFFFFFFFF, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Sidebar
        renderSidebar(context, x, y);
        // Main
        renderMain(context, x + 70, y + 40, mouseX, mouseY);
    }

    private void renderSidebar(DrawContext context, int x, int y) {
        String[][] coins = {{"bitcoin","BTC"},{"ethereum","ETH"},{"solana","SOL"},{"dogecoin","DOGE"}};
        for(int i=0; i<coins.length; i++) {
            boolean active = selectedCoin.equals(coins[i][0]);
            int sy = y + 40 + (i * 25);
            context.fill(x + 5, sy, x + 60, sy + 20, active ? 0xFF2B2F36 : 0xFF181A20);
            context.drawText(textRenderer, coins[i][1], x + 10, sy + 6, active ? 0xFFF0B90B : 0xFFFFFFFF, false);
            if (active) context.fill(x + 5, sy, x + 7, sy + 20, 0xFFF0B90B);
        }
    }

    private void renderMain(DrawContext context, int x, int y, int mouseX, int mouseY) {
        double price = prices.getOrDefault(selectedCoin, 0.0);
        double avg = averages.getOrDefault(selectedCoin, 0.0);

        context.drawText(textRenderer, selectedCoin.toUpperCase(), x, y, 0xFFFFFFFF, false);
        String priceStr = String.format("%.2f €", price);
        context.drawText(textRenderer, priceStr, x, y + 12, 0xFFF0B90B, false);

        if (avg > 0) {
            double pl = ((price - avg) / avg) * 100.0;
            String plStr = String.format("P/L %s%.2f%%", pl >= 0 ? "+" : "", pl);
            context.drawText(textRenderer, plStr, x, y + 24, pl >= 0 ? 0xFF2ECC71 : 0xFFE74C3C, false);
        }

        // Amount Selection
        context.drawText(textRenderer, "MONTANT:", x, y + 50, 0xFF707A8A, false);
        double[] amts = {0.01, 0.1, 1.0, 5.0};
        for(int i=0; i<amts.length; i++) {
            renderAmtBtn(context, x + (i*42), y + 62, amts[i]);
        }

        // Trade Buttons
        renderBtn(context, x, y + 95, 80, 25, "ACHETER", 0xFF2ECC71, mouseX, mouseY);
        renderBtn(context, x + 90, y + 95, 80, 25, "VENDRE", 0xFFE74C3C, mouseX, mouseY);
    }

    private void renderAmtBtn(DrawContext context, int x, int y, double amt) {
        boolean active = selectedAmount == amt;
        context.fill(x, y, x + 38, y + 15, active ? 0xFFF0B90B : 0xFF2B2F36);
        String s = String.valueOf(amt);
        context.drawText(textRenderer, s, x + 19 - textRenderer.getWidth(s)/2, y + 4, active ? 0xFF000000 : 0xFFFFFFFF, false);
    }

    private void renderBtn(DrawContext context, int x, int y, int w, int h, String txt, int color, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x+w && mouseY >= y && mouseY <= y+h;
        context.fill(x, y, x + w, y + h, hover ? color : color - 0x44000000);
        context.drawText(textRenderer, txt, x + w/2 - textRenderer.getWidth(txt)/2, y + h/2 - 4, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Sidebar click
        if (mx >= x + 5 && mx <= x + 60) {
            if (my >= y + 40 && my <= y + 60) selectedCoin = "bitcoin";
            else if (my >= y + 65 && my <= y + 85) selectedCoin = "ethereum";
            else if (my >= y + 90 && my <= y + 110) selectedCoin = "solana";
            else if (my >= y + 115 && my <= y + 135) selectedCoin = "dogecoin";
        }

        // Amount click
        int ax = x + 70;
        int ay = y + 40 + 62;
        if (my >= ay && my <= ay + 15) {
            double[] amts = {0.01, 0.1, 1.0, 5.0};
            for(int i=0; i<amts.length; i++) {
                if (mx >= ax + (i*42) && mx <= ax + (i*42) + 38) selectedAmount = amts[i];
            }
        }

        // Action Buttons
        int bx = x + 70;
        int by = y + 40 + 95;
        if (my >= by && my <= by + 25) {
            double price = prices.getOrDefault(selectedCoin, 0.0);
            if (mx >= bx && mx <= bx + 80) sendAction("buy", price, selectedAmount);
            else if (mx >= bx + 90 && mx <= bx + 170) sendAction("sell", price, selectedAmount);
        }

        return super.mouseClicked(mx, my, button);
    }

    private void sendAction(String type, double price, double amount) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new Networking.CryptoActionPayload(type, selectedCoin, price, amount));
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) { }
}
