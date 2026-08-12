/**
 * ShaiCraft Economy - Player-to-Player Trading Interface
 */
package fr.shai.shaicrafteconomy.gui;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.trade.TradeManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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

import java.util.List;
import java.util.UUID;

public class PlayerTradeGui implements NamedScreenHandlerFactory {

    private final TradeManager.TradeSession session;
    private final ServerPlayerEntity viewer;

    public PlayerTradeGui(TradeManager.TradeSession session, ServerPlayerEntity viewer) {
        this.session = session;
        this.viewer = viewer;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Troc : " + session.player1.getName().getString() + " & " + session.player2.getName().getString()).formatted(Formatting.BOLD, Formatting.GOLD);
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        buildMiddleColumn();

        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, session.inventory, 6) {
            @Override
            public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity p) {
                if (slotIndex < 0 || slotIndex >= 54) {
                    super.onSlotClick(slotIndex, button, actionType, p);
                    return;
                }

                boolean isViewerP1 = session.isPlayer1(p.getUuid());
                int col = slotIndex % 9;
                
                // Allow interactions only on the player's side
                if (isViewerP1) {
                    if (col >= 0 && col <= 3) {
                        unready(session);
                        super.onSlotClick(slotIndex, button, actionType, p);
                    }
                } else {
                    if (col >= 5 && col <= 8) {
                        unready(session);
                        super.onSlotClick(slotIndex, button, actionType, p);
                    }
                }

                // Middle column interactions
                if (col == 4) {
                    // Row 3 (slot 31): Add Money
                    if (slotIndex == 31) {
                        ShaiCraftEconomy.getInstance().getTradeSessionManager().requestMoneyInput((ServerPlayerEntity) p);
                    }
                    // Row 5 (slot 53? no, row 5 col 4 is slot 49): Ready button
                    else if (slotIndex == 49) {
                        if (isViewerP1) {
                            session.p1Ready = !session.p1Ready;
                        } else {
                            session.p2Ready = !session.p2Ready;
                        }
                        
                        buildMiddleColumn(); // Update visual state
                        
                        // Sync GUI state for both
                        if (session.player1.currentScreenHandler instanceof GenericContainerScreenHandler) {
                            session.player1.currentScreenHandler.sendContentUpdates();
                        }
                        if (session.player2.currentScreenHandler instanceof GenericContainerScreenHandler) {
                            session.player2.currentScreenHandler.sendContentUpdates();
                        }
                        
                        checkExecute(); // Check if both are ready
                    }
                }
            }

            @Override
            public boolean canUse(PlayerEntity player) {
                return true;
            }

            @Override
            public void onClosed(PlayerEntity player) {
                super.onClosed(player);
                // If GUI is closed and not executed (session still exists in manager), we cancel it
                // Except if player is awaiting chat input
                TradeManager mgr = ShaiCraftEconomy.getInstance().getTradeSessionManager();
                if (mgr.getSession(player.getUuid()) != null) {
                    session.cancel();
                    mgr.endSession(session);
                }
            }
        };
    }

    private void unready(TradeManager.TradeSession session) {
        session.p1Ready = false;
        session.p2Ready = false;
        buildMiddleColumn();
    }

    private void checkExecute() {
        if (session.p1Ready && session.p2Ready) {
            session.execute();
            ShaiCraftEconomy.getInstance().getTradeSessionManager().endSession(session);
        }
    }

    private void buildMiddleColumn() {
        // Slot 4 - Player 1 Information & Money
        ItemStack p1Info = new ItemStack(Items.PLAYER_HEAD);
        p1Info.set(DataComponentTypes.CUSTOM_NAME, Text.literal(session.player1.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD));
        p1Info.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Argent proposé : ").formatted(Formatting.GRAY).append(Text.literal(String.format("%,.2f€", session.p1Money)).formatted(Formatting.YELLOW))
        )));
        session.inventory.setStack(4, p1Info);

        // Slot 13 - Player 2 Information & Money
        ItemStack p2Info = new ItemStack(Items.PLAYER_HEAD);
        p2Info.set(DataComponentTypes.CUSTOM_NAME, Text.literal(session.player2.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD));
        p2Info.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Argent proposé : ").formatted(Formatting.GRAY).append(Text.literal(String.format("%,.2f€", session.p2Money)).formatted(Formatting.YELLOW))
        )));
        session.inventory.setStack(13, p2Info);

        // Slot 22 - Glass separator
        session.inventory.setStack(22, glass(Items.BLACK_STAINED_GLASS_PANE));

        // Slot 31 - Add Money Button
        ItemStack addMoney = new ItemStack(Items.GOLD_INGOT);
        addMoney.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Miser de l'argent").formatted(Formatting.GOLD, Formatting.BOLD));
        addMoney.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Cliquez pour proposer un montant en €").formatted(Formatting.GRAY)
        )));
        session.inventory.setStack(31, addMoney);

        // Slot 40 - Glass separator
        session.inventory.setStack(40, glass(Items.BLACK_STAINED_GLASS_PANE));

        // Slot 49 - Ready Button
        boolean isViewerP1 = session.isPlayer1(viewer.getUuid());
        boolean amIReady = isViewerP1 ? session.p1Ready : session.p2Ready;
        boolean isOtherReady = isViewerP1 ? session.p2Ready : session.p1Ready;

        Item readyItem = (amIReady && isOtherReady) ? Items.EMERALD_BLOCK : (amIReady ? Items.LIME_DYE : Items.GRAY_DYE);
        ItemStack readyBtn = new ItemStack(readyItem);
        readyBtn.set(DataComponentTypes.CUSTOM_NAME, Text.literal(amIReady ? "Prêt !" : "Cliquez quand vous êtes prêt").formatted(amIReady ? Formatting.GREEN : Formatting.YELLOW, Formatting.BOLD));
        
        List<Text> lore = new java.util.ArrayList<>();
        lore.add(Text.literal(session.player1.getName().getString() + " : ").formatted(Formatting.GRAY)
            .append(Text.literal(session.p1Ready ? "P" : "Attente").formatted(session.p1Ready ? Formatting.GREEN : Formatting.RED)));
        lore.add(Text.literal(session.player2.getName().getString() + " : ").formatted(Formatting.GRAY)
            .append(Text.literal(session.p2Ready ? "P" : "Attente").formatted(session.p2Ready ? Formatting.GREEN : Formatting.RED)));
        readyBtn.set(DataComponentTypes.LORE, new LoreComponent(lore));
        
        session.inventory.setStack(49, readyBtn);
    }

    private ItemStack glass(Item item) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return s;
    }
}
