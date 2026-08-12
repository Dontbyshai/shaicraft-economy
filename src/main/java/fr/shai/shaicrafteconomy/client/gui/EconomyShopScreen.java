/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.client.gui;

import fr.shai.shaicrafteconomy.gui.EconomyShopScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class EconomyShopScreen extends HandledScreen<EconomyShopScreenHandler> {
    private int activeTab = 0; // 0: Buy, 1: Sell, 2: Pokemon
    private double balance = 0.0;
    private java.util.Map<String, fr.shai.shaicrafteconomy.market.MarketManager.MarketItem> marketData = new java.util.HashMap<>();
    private java.util.Map<String, Double> portfolioData = new java.util.HashMap<>();
    private java.util.Map<String, Integer> investmentLevels = new java.util.HashMap<>();

    public EconomyShopScreen(EconomyShopScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 256;
        this.backgroundHeight = 220;
    }

    public void updateBalance(double balance) {
        this.balance = balance;
    }

    public void updateMarket(java.util.Map<String, fr.shai.shaicrafteconomy.market.MarketManager.MarketItem> data) {
        this.marketData.putAll(data);
    }

    public void updatePortfolio(java.util.Map<String, Double> data) {
        this.portfolioData.clear();
        this.portfolioData.putAll(data);
    }

    public void updateInvestments(java.util.Map<String, Integer> data) {
        this.investmentLevels.clear();
        this.investmentLevels.putAll(data);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Dark modern background (crypto-style)
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF101015); // Slightly off-black
        context.fill(x + 2, y + 2, x + backgroundWidth - 2, y + backgroundHeight - 2, 0xFF1E1E1E); // Main dark gray area
        
        // Tab bar background
        context.fill(x, y, x + backgroundWidth, y + 35, 0xFF1C1C24); // Even darker for header
        
        // Border
        renderBorder(context, x, y, backgroundWidth, backgroundHeight, 0xFF333333);
    }

    private void renderBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Render Balance (Euros)
        String balStr = String.format("%.2f €", balance);
        context.drawText(textRenderer, Text.literal(balStr).formatted(Formatting.GREEN, Formatting.BOLD), x + backgroundWidth - 10 - textRenderer.getWidth(balStr), y + 12, 0xFFFFFFFF, false);

        // Render Tabs
        renderTab(context, x + 10, y + 7, Text.translatable("gui.shaicraft-economy.tab.items").getString(), activeTab == 0);
        renderTab(context, x + 70, y + 7, Text.translatable("gui.shaicraft-economy.tab.selling").getString(), activeTab == 1);
        renderTab(context, x + 130, y + 7, Text.translatable("gui.shaicraft-economy.tab.pokemon").getString(), activeTab == 2);

        // Render Content based on tab
        renderContent(context, x, y, mouseX, mouseY);

        // Render Branding Footer
        String footer = "ShaiCraft Economy by Shaï";
        context.drawText(textRenderer, "NIVEAUX INVESTISSEMENTS", x + backgroundWidth - 130, y + backgroundHeight - 40, 0xFF707080, false);
    }

    private void renderContent(DrawContext context, int x, int y, int mouseX, int mouseY) {
        if (activeTab == 0) {
            renderItemCard(context, x + 15, y + 40, net.minecraft.item.Items.DIAMOND, "minecraft:diamond", "BUY", mouseX, mouseY);
            renderItemCard(context, x + 15 + 60, y + 40, net.minecraft.item.Items.IRON_INGOT, "minecraft:iron_ingot", "BUY", mouseX, mouseY);
            renderItemCard(context, x + 15 + 120, y + 40, net.minecraft.item.Items.GOLD_INGOT, "minecraft:gold_ingot", "BUY", mouseX, mouseY);
        } else if (activeTab == 1) {
            renderItemCard(context, x + 15, y + 40, net.minecraft.item.Items.DIAMOND, "minecraft:diamond", "SELL", mouseX, mouseY);
            renderItemCard(context, x + 15 + 60, y + 40, net.minecraft.item.Items.IRON_INGOT, "minecraft:iron_ingot", "SELL", mouseX, mouseY);
            renderItemCard(context, x + 15 + 120, y + 40, net.minecraft.item.Items.GOLD_INGOT, "minecraft:gold_ingot", "SELL", mouseX, mouseY);
        } else if (activeTab == 2) {
            renderPokemonCard(context, x + 15, y + 40, "Pikachu", "pikachu", mouseX, mouseY);
            renderPokemonCard(context, x + 15 + 80, y + 40, "Mewtwo", "mewtwo", mouseX, mouseY);
        } else if (activeTab == 3) {
            renderInvestmentCard(context, x + 15, y + 40, "diamond_mine", "Diamond Mine", 0xFF9B59B6, mouseX, mouseY);
            renderInvestmentCard(context, x + 15 + 80, y + 40, "iron_farm", "Iron Farm", 0xFF9B59B6, mouseX, mouseY);
            renderInvestmentCard(context, x + 15 + 160, y + 40, "pokemon_breedery", "Breeding", 0xFF9B59B6, mouseX, mouseY);
        }
    }

    private void renderItemCard(DrawContext context, int x, int y, net.minecraft.item.Item item, String id, String action, int mouseX, int mouseY) {
        fr.shai.shaicrafteconomy.market.MarketManager.MarketItem mItem = marketData.get(id);
        double price = mItem != null ? mItem.currentPrice : 0.0;

        boolean hover = mouseX >= x && mouseX <= x + 50 && mouseY >= y && mouseY <= y + 70;
        context.fill(x, y, x + 50, y + 70, hover ? 0xFF2A2A2A : 0xFF222222);
        renderBorder(context, x, y, 50, 70, action.equals("BUY") ? 0xFF3D85C6 : 0xFFE67E22);
        
        context.drawItem(new net.minecraft.item.ItemStack(item), x + 17, y + 10);
        String name = item.getName().getString();
        if (name.length() > 10) name = name.substring(0, 8) + "..";
        context.drawText(textRenderer, name, x + 25 - textRenderer.getWidth(name)/2, y + 35, 0xFFCCCCCC, false);
        
        String priceStr = String.format("%.1f€", price);
        context.drawText(textRenderer, priceStr, x + 25 - textRenderer.getWidth(priceStr)/2, y + 55, 0xFFF1C40F, false);

        if (hover) {
            context.drawText(textRenderer, "PORTEFEUILLE", x + 10, y + backgroundHeight - 40, 0xFF707080, false);
        }
    }

    private void renderPokemonCard(DrawContext context, int x, int y, String name, String id, int mouseX, int mouseY) {
        fr.shai.shaicrafteconomy.market.MarketManager.MarketItem mItem = marketData.get(id);
        double price = mItem != null ? mItem.currentPrice : 0.0;
        double change = mItem != null ? mItem.lastChangePercent : 0.0;

        boolean hover = mouseX >= x && mouseX <= x + 70 && mouseY >= y && mouseY <= y + 70;
        context.fill(x, y, x + 70, y + 70, hover ? 0xFF2A2A2A : 0xFF222222);
        renderBorder(context, x, y, 70, 70, hover ? 0xFFFFD700 : 0xFF333333);
        
        context.drawText(textRenderer, name, x + 35 - textRenderer.getWidth(name)/2, y + 20, 0xFFFFFFFF, false);
        String priceStr = String.format("%.1f€", price);
        context.drawText(textRenderer, priceStr, x + 35 - textRenderer.getWidth(priceStr)/2, y + 40, 0xFFF1C40F, false);

        String changeStr = String.format("%s%.1f%%", change >= 0 ? "+" : "", change);
        int changeColor = change >= 0 ? 0xFF2ECC71 : 0xFFE74C3C;
        context.drawText(textRenderer, changeStr, x + 35 - textRenderer.getWidth(changeStr)/2, y + 50, changeColor, false);

        Double avg = portfolioData.get(id);
        if (avg != null && avg > 0) {
            double pl = ((price - avg) / avg) * 100.0;
            String plStr = String.format("P/L %s%.0f%%", pl >= 0 ? "+" : "", pl);
            context.drawText(textRenderer, plStr, x + 35 - textRenderer.getWidth(plStr)/2, y + 60, pl >= 0 ? 0xFF2ECC71 : 0xFFE74C3C, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        if (mouseY >= y + 5 && mouseY <= y + 25) {
            if (mouseX >= x + 10 && mouseX <= x + 60) activeTab = 0;
            else if (mouseX >= x + 70 && mouseX <= x + 120) activeTab = 1;
            else if (mouseX >= x + 130 && mouseX <= x + 180) activeTab = 2;
            return true;
        }

        if (activeTab == 0) {
            if (checkItemClick(x + 15, y + 40, "minecraft:diamond", getMarketPrice("minecraft:diamond"), "item", mouseX, mouseY)) return true;
            if (checkItemClick(x + 15 + 60, y + 40, "minecraft:iron_ingot", getMarketPrice("minecraft:iron_ingot"), "item", mouseX, mouseY)) return true;
            if (checkItemClick(x + 15 + 120, y + 40, "minecraft:gold_ingot", getMarketPrice("minecraft:gold_ingot"), "item", mouseX, mouseY)) return true;
        } else if (activeTab == 1) {
            if (checkItemClick(x + 15, y + 40, "minecraft:diamond", getMarketPrice("minecraft:diamond"), "sell", mouseX, mouseY)) return true;
            if (checkItemClick(x + 15 + 60, y + 40, "minecraft:iron_ingot", getMarketPrice("minecraft:iron_ingot"), "sell", mouseX, mouseY)) return true;
            if (checkItemClick(x + 15 + 120, y + 40, "minecraft:gold_ingot", getMarketPrice("minecraft:gold_ingot"), "sell", mouseX, mouseY)) return true;
        } else if (activeTab == 2) {
            if (checkPokemonClick(x + 15, y + 40, "pikachu", getMarketPrice("pikachu"), mouseX, mouseY)) return true;
            if (checkPokemonClick(x + 15 + 80, y + 40, "mewtwo", getMarketPrice("mewtwo"), mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private double getMarketPrice(String id) {
        fr.shai.shaicrafteconomy.market.MarketManager.MarketItem item = marketData.get(id);
        return item != null ? item.currentPrice : 0.0;
    }

    private boolean checkItemClick(int x, int y, String id, double price, String type, double mx, double my) {
        if (mx >= x && mx <= x + 50 && my >= y && my <= y + 70) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new fr.shai.shaicrafteconomy.network.Networking.PurchasePayload(type, id, price));
            return true;
        }
        return false;
    }

    private boolean checkPokemonClick(int x, int y, String id, double price, double mx, double my) {
        if (mx >= x && mx <= x + 70 && my >= y && my <= y + 70) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new fr.shai.shaicrafteconomy.network.Networking.PurchasePayload("pokemon", id, price));
            return true;
        }
        return false;
    }

    private boolean checkInvestmentClick(int x, int y, String id, double mx, double my) {
        if (mx >= x + backgroundWidth - 100 && mx <= x + backgroundWidth - 10 && my >= y + backgroundHeight - 25 && my <= y + backgroundHeight - 10) {
            this.client.setScreen(new TradingScreen(new fr.shai.shaicrafteconomy.gui.TradingScreenHandler(0, this.client.player.getInventory()), this.client.player.getInventory(), Text.literal("Trading")));
            return true;
        }
        return false;
    }

    private void renderInvestmentCard(DrawContext context, int x, int y, String id, String name, int color, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + 70 && mouseY >= y && mouseY <= y + 70;
        context.fill(x, y, x + 70, y + 70, hover ? 0xFF2A2A2A : 0xFF222222);
        renderBorder(context, x, y, 70, 70, color);
        
        context.drawText(textRenderer, name, x + 35 - textRenderer.getWidth(name)/2, y + 30, 0xFFFFFFFF, false);
    }

    private void renderTab(DrawContext context, int x, int y, String label, boolean active) {
        int color = active ? 0xFFFFFFFF : 0xFF888888;
        context.drawText(textRenderer, Text.literal(label).formatted(active ? Formatting.BOLD : Formatting.RESET), x, y, color, false);
        if (active) {
            context.fill(x, y + 10, x + textRenderer.getWidth(label), y + 11, 0xFF3D85C6); // Blue underline for active tab
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // We handle this in render
    }
}
