/**
 * ShaiCraft Economy – stub kept for client-side TradingScreen compatibility.
 * The actual server-side GUI logic lives in TradingGui.
 */
package fr.shai.shaicrafteconomy.gui;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public class TradingScreenHandler extends ScreenHandler {
    public TradingScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ShaiCraftEconomy.SHOP_SCREEN_HANDLER, syncId);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
