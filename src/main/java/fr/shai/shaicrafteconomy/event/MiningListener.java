/**
 * ShaiCraft Economy - Minecraft Fabric Mod
 * Created by Shaï
 */
package fr.shai.shaicrafteconomy.event;

import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class MiningListener {
    private static final Map<Block, Double> REWARDS = new HashMap<>();

    static {
        REWARDS.put(Blocks.DIAMOND_ORE, 50.0);
        REWARDS.put(Blocks.DEEPSLATE_DIAMOND_ORE, 50.0);
        REWARDS.put(Blocks.EMERALD_ORE, 30.0);
        REWARDS.put(Blocks.DEEPSLATE_EMERALD_ORE, 30.0);
        REWARDS.put(Blocks.GOLD_ORE, 10.0);
        REWARDS.put(Blocks.DEEPSLATE_GOLD_ORE, 10.0);
        REWARDS.put(Blocks.IRON_ORE, 5.0);
        REWARDS.put(Blocks.DEEPSLATE_IRON_ORE, 5.0);
        REWARDS.put(Blocks.COAL_ORE, 1.0);
        REWARDS.put(Blocks.DEEPSLATE_COAL_ORE, 1.0);
        REWARDS.put(Blocks.ANCIENT_DEBRIS, 100.0);
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient) return;

            Double reward = REWARDS.get(state.getBlock());
            if (reward != null && player instanceof ServerPlayerEntity serverPlayer) {
                ShaiCraftEconomy.getInstance().getEconomyManager().addBalance(player.getUuid(), reward);
                player.sendMessage(Text.translatable("msg.shaicraft-economy.mining_reward", String.format("%.2f", reward)).formatted(Formatting.GREEN));
            }
        });
    }
}
