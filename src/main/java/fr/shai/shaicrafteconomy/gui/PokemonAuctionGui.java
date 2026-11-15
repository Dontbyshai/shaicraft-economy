/**
 * ShaiCraft Economy - Hôtel des Ventes Pokémon GUI
 */
package fr.shai.shaicrafteconomy.gui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import fr.shai.shaicrafteconomy.ShaiCraftEconomy;
import fr.shai.shaicrafteconomy.auction.PokemonAuctionManager;
import fr.shai.shaicrafteconomy.auction.PokemonAuctionManager.AuctionListing;
import fr.shai.shaicrafteconomy.economy.EconomyManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
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

public class PokemonAuctionGui implements NamedScreenHandlerFactory {

    private enum AuctionView { BROWSE, MY_LISTINGS, SELL_CONFIRM }

    private AuctionView view = AuctionView.BROWSE;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 45;

    // When in SELL_CONFIRM mode: which party slot is being listed
    private int sellPartySlot = -1;
    private double sellPrice  = 0;

    @Override
    public Text getDisplayName() {
        return switch (view) {
            case MY_LISTINGS  -> Text.literal("🏛 HDV – Mes annonces").formatted(Formatting.BOLD, Formatting.GOLD);
            case SELL_CONFIRM -> Text.literal("🏛 HDV – Mettre en vente").formatted(Formatting.BOLD, Formatting.RED);
            default           -> Text.literal("🏛 Hôtel des Ventes Pokémon").formatted(Formatting.BOLD, Formatting.YELLOW);
        };
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(54);
        populateInventory(inv, (ServerPlayerEntity) player);

        return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inv, 6) {
            @Override
            public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
                if (slot < 0 || slot >= 54) { super.onSlotClick(slot, button, action, player); return; }
                ItemStack clicked = inv.getStack(slot);
                if (clicked.isEmpty()) return;
                ServerPlayerEntity sp = (ServerPlayerEntity) player;
                String tag = getTag(clicked);
                if (tag == null) return;

                switch (tag) {
                    case "browse"     -> { view = AuctionView.BROWSE;      page = 0; reopen(sp); }
                    case "my_list"    -> { view = AuctionView.MY_LISTINGS; page = 0; reopen(sp); }
                    case "prev"       -> { if (page > 0) { page--; reopen(sp); } }
                    case "next"       -> { page++; reopen(sp); }
                    default -> handleAction(sp, tag, button);
                }
            }

            @Override public boolean canUse(PlayerEntity p) { return true; }

            private void reopen(ServerPlayerEntity sp) {
                sp.closeHandledScreen();
                sp.openHandledScreen(PokemonAuctionGui.this);
            }
        };
    }

    private void populateInventory(SimpleInventory inv, ServerPlayerEntity player) {
        switch (view) {
            case BROWSE      -> buildBrowse(inv, player);
            case MY_LISTINGS -> buildMyListings(inv, player);
        }
    }

    // ── Browse all listings ───────────────────────────────────────────────────
    private void buildBrowse(SimpleInventory inv, ServerPlayerEntity player) {
        PokemonAuctionManager mgr = ShaiCraftEconomy.getInstance().getAuctionManager();
        List<AuctionListing> all  = mgr.getListings();

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, all.size());

        for (int i = start; i < end; i++) {
            AuctionListing listing = all.get(i);
            boolean isMine = listing.sellerUuid().equals(player.getUuid().toString());

            ItemStack egg = tagged(Items.EGG, "buy:" + listing.id());
            egg.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(capitalize(listing.pokemonSpecies()) + " Niv." + listing.pokemonLevel())
                    .formatted(Formatting.BOLD, isMine ? Formatting.GOLD : Formatting.WHITE));

            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal("Vendeur: " + listing.sellerName()).formatted(Formatting.GRAY));
            lines.add(Text.literal("Prix: ").formatted(Formatting.GRAY)
                .append(Text.literal(fmt(listing.price()) + "€").formatted(Formatting.YELLOW)));
            lines.add(Text.empty());
            if (isMine) {
                lines.add(Text.literal("C'est votre annonce").formatted(Formatting.GOLD));
                lines.add(Text.literal("Clic droit = retirer").formatted(Formatting.RED));
            } else {
                lines.add(Text.literal("Clic gauche = acheter").formatted(Formatting.GREEN));
            }
            egg.set(DataComponentTypes.LORE, new LoreComponent(lines));
            inv.setStack(i - start, egg);
        }

        // Navigation
        addBottomNav(inv, start > 0, end < all.size(), true);

        if (all.isEmpty()) {
            ItemStack empty = new ItemStack(Items.BARRIER);
            empty.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Aucune annonce disponible").formatted(Formatting.RED));
            inv.setStack(22, empty);
        }
    }

    // ── My listings ───────────────────────────────────────────────────────────
    private void buildMyListings(SimpleInventory inv, ServerPlayerEntity player) {
        PokemonAuctionManager mgr = ShaiCraftEconomy.getInstance().getAuctionManager();
        PlayerPartyStore party    = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<AuctionListing> mine = mgr.getListingsBySeller(player.getUuid().toString());

        // Show party (first 6 slots → row 1)
        for (int i = 0; i < 6; i++) {
            Pokemon poke = party.get(i);
            if (poke == null) {
                ItemStack empty = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                empty.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Slot vide").formatted(Formatting.GRAY));
                inv.setStack(i, empty);
            } else {
                ItemStack egg = tagged(Items.EGG, "list_party:" + i);
                egg.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(poke.getDisplayName(true).getString() + " Niv." + poke.getLevel())
                        .formatted(Formatting.AQUA, Formatting.BOLD));
                List<Text> lines = new ArrayList<>();
                lines.add(Text.literal("Cliquez pour mettre en vente").formatted(Formatting.YELLOW));
                lines.add(Text.literal("Prix suggéré: " + fmt(suggestPrice(poke)) + "€").formatted(Formatting.GRAY));
                egg.set(DataComponentTypes.LORE, new LoreComponent(lines));
                inv.setStack(i, egg);
            }
        }

        // Label row
        ItemStack sep = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
        sep.set(DataComponentTypes.CUSTOM_NAME, Text.literal("── Vos annonces actives ──").formatted(Formatting.YELLOW));
        for (int i = 9; i < 18; i++) inv.setStack(i, sep.copy());

        // Show existing listings
        for (int i = 0; i < Math.min(mine.size(), 27); i++) {
            AuctionListing l = mine.get(i);
            ItemStack egg = tagged(Items.EGG, "cancel:" + l.id());
            egg.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(capitalize(l.pokemonSpecies()) + " Niv." + l.pokemonLevel())
                    .formatted(Formatting.GOLD, Formatting.BOLD));
            egg.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Prix: " + fmt(l.price()) + "€").formatted(Formatting.YELLOW),
                Text.empty(),
                Text.literal("Clic = retirer l'annonce").formatted(Formatting.RED)
            )));
            inv.setStack(18 + i, egg);
        }

        addBottomNav(inv, false, false, false);
    }

    // ── Action handler ────────────────────────────────────────────────────────
    private void handleAction(ServerPlayerEntity sp, String tag, int button) {
        PokemonAuctionManager mgr = ShaiCraftEconomy.getInstance().getAuctionManager();
        EconomyManager eco        = ShaiCraftEconomy.getInstance().getEconomyManager();

        // Buy listing
        if (tag.startsWith("buy:")) {
            String listingId = tag.substring(4);
            if (button == 1) {
                // Right click on own listing → cancel
                var listing = mgr.findById(listingId);
                if (listing.isPresent() && listing.get().sellerUuid().equals(sp.getUuid().toString())) {
                    cancelAndReturnPokemon(sp, listing.get(), mgr);
                    return;
                }
            }
            // Left click → buy
            var optListing = mgr.findById(listingId);
            if (optListing.isEmpty()) {
                sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_gone").formatted(Formatting.RED));
                return;
            }
            AuctionListing listing = optListing.get();
            if (listing.sellerUuid().equals(sp.getUuid().toString())) {
                sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_own").formatted(Formatting.RED));
                return;
            }
            if (!eco.removeBalance(sp.getUuid(), listing.price())) {
                sp.sendMessage(Text.translatable("msg.shaicraft-economy.insufficient_funds").formatted(Formatting.RED));
                return;
            }
            // Give pokemon to buyer
            Pokemon poke = reconstructPokemon(listing.pokemonNbt(), listing.pokemonSpecies(), listing.pokemonLevel());
            addPokemonToPlayer(sp, poke);
            // Pay seller
            UUID sellerUuid = UUID.fromString(listing.sellerUuid());
            eco.addBalance(sellerUuid, listing.price());
            ShaiCraftEconomy.getInstance().getTransactionLog().add(sp.getUuid(), "AUCTION_BUY", -listing.price(), listing.pokemonSpecies());
            ShaiCraftEconomy.getInstance().getTransactionLog().add(sellerUuid, "AUCTION_SELL", listing.price(), listing.pokemonSpecies());
            mgr.removeListing(listing.id());
            sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_bought",
                capitalize(listing.pokemonSpecies()), listing.pokemonLevel(), fmt(listing.price())).formatted(Formatting.GREEN));
            // Notify seller if online
            sp.getServer().getPlayerManager().getPlayer(sellerUuid);
            return;
        }

        // List a party pokémon
        if (tag.startsWith("list_party:")) {
            int slot = Integer.parseInt(tag.substring(11));
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(sp);
            Pokemon poke = party.get(slot);
            if (poke == null) return;
            double price = suggestPrice(poke);
            String nbt   = serializePokemon(poke);
            AuctionListing listing = new AuctionListing(
                UUID.randomUUID().toString(),
                sp.getUuid().toString(),
                sp.getGameProfile().getName(),
                poke.getSpecies().getName(),
                poke.getLevel(),
                nbt,
                price,
                System.currentTimeMillis()
            );
            party.remove(poke);
            mgr.addListing(listing);
            sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_listed", poke.getDisplayName(true).getString(), fmt(price)).formatted(Formatting.GREEN));
            sp.closeHandledScreen();
            sp.openHandledScreen(this);
            return;
        }

        // Cancel own listing
        if (tag.startsWith("cancel:")) {
            String listId = tag.substring(7);
            mgr.findById(listId).ifPresent(l -> {
                if (l.sellerUuid().equals(sp.getUuid().toString())) {
                    cancelAndReturnPokemon(sp, l, mgr);
                }
            });
        }
    }

    private void cancelAndReturnPokemon(ServerPlayerEntity sp, AuctionListing l, PokemonAuctionManager mgr) {
        mgr.removeListing(l.id());
        Pokemon poke = reconstructPokemon(l.pokemonNbt(), l.pokemonSpecies(), l.pokemonLevel());
        addPokemonToPlayer(sp, poke);
        sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_cancelled", capitalize(l.pokemonSpecies())).formatted(Formatting.YELLOW));
        sp.closeHandledScreen();
        sp.openHandledScreen(this);
    }

    private void addPokemonToPlayer(ServerPlayerEntity sp, Pokemon poke) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(sp);
        if (!party.add(poke)) {
            Cobblemon.INSTANCE.getStorage().getPC(sp).add(poke);
            sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_pc").formatted(Formatting.YELLOW));
        }
    }

    private String serializePokemon(Pokemon poke) {
        // We store species+level in the listing directly; full NBT requires
        // a server DynamicRegistryManager we don't have here statically.
        return "";
    }

    private Pokemon reconstructPokemon(String nbt, String species, int level) {
        // Reconstruct from species + level (reliable cross-server approach)
        return PokemonProperties.Companion.parse("species=" + species + " level=" + level).create();
    }

    private double suggestPrice(Pokemon poke) {
        double base = 5000;
        base += poke.getLevel() * 500;
        if (poke.getShiny()) base *= 5;
        if (poke.isLegendary()) base *= 10;
        return base;
    }

    // ── Bottom navigation ─────────────────────────────────────────────────────
    private void addBottomNav(SimpleInventory inv, boolean hasPrev, boolean hasNext, boolean showBrowseBtn) {
        // Browse tab (slot 46)
        ItemStack browse = tagged(Items.COMPASS, showBrowseBtn ? "my_list" : "browse");
        browse.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(showBrowseBtn ? "📋 Mes annonces" : "🔍 Parcourir").formatted(Formatting.BOLD, Formatting.YELLOW));
        inv.setStack(46, browse);

        if (hasPrev) {
            ItemStack prev = tagged(Items.ARROW, "prev");
            prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("◀ Page précédente").formatted(Formatting.YELLOW, Formatting.BOLD));
            inv.setStack(45, prev);
        }
        if (hasNext) {
            ItemStack next = tagged(Items.SPECTRAL_ARROW, "next");
            next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Page suivante ▶").formatted(Formatting.YELLOW, Formatting.BOLD));
            inv.setStack(53, next);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private ItemStack tagged(net.minecraft.item.Item item, String tag) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_DATA,
            net.minecraft.nbt.NbtCompound.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"shopTag\":\"" + tag + "\"}")
            ).result().map(nbt -> net.minecraft.component.type.NbtComponent.of(nbt)).orElse(net.minecraft.component.type.NbtComponent.DEFAULT)
        );
        return stack;
    }

    private String getTag(ItemStack stack) {
        var data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        var nbt = data.copyNbt();
        if (!nbt.contains("shopTag")) return null;
        return nbt.getString("shopTag");
    }

    private String fmt(double v) {
        return v >= 1000 ? String.format("%,.0f", v) : String.format("%.0f", v);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    public static void listPokemon(ServerPlayerEntity sp, int partySlot, double price) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(sp);
        Pokemon poke = party.get(partySlot);
        if (poke == null) {
            sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_no_pokemon").formatted(Formatting.RED));
            return;
        }

        PokemonAuctionManager mgr = ShaiCraftEconomy.getInstance().getAuctionManager();
        String nbt = ""; // Same as GUI logic
        AuctionListing listing = new AuctionListing(
            UUID.randomUUID().toString(),
            sp.getUuid().toString(),
            sp.getGameProfile().getName(),
            poke.getSpecies().getName(),
            poke.getLevel(),
            nbt,
            price,
            System.currentTimeMillis()
        );
        party.remove(poke);
        mgr.addListing(listing);
        sp.sendMessage(Text.translatable("msg.shaicraft-economy.hdv_listed", poke.getDisplayName(true).getString(), String.format("%.2f", price)).formatted(Formatting.GREEN));
    }
}

