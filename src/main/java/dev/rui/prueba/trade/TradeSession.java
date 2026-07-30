package dev.rui.prueba.trade;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.util.Messages;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class TradeSession {

    private static final int[] OWN_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] MIRROR_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private static final int CONFIRM_SLOT = 46;
    private static final int SUMMARY_SLOT = 49;
    private static final int PARTNER_SLOT = 52;
    private static final boolean[] IS_OWN = new boolean[54];

    static {
        for (int slot : OWN_SLOTS) {
            IS_OWN[slot] = true;
        }
    }

    private final Prueba plugin;
    private final TradeManager manager;
    private final Player first;
    private final Player second;
    private final Inventory firstView;
    private final Inventory secondView;
    private final ItemStack[] firstOffer = new ItemStack[OWN_SLOTS.length];
    private final ItemStack[] secondOffer = new ItemStack[OWN_SLOTS.length];
    private boolean firstReady;
    private boolean secondReady;
    private boolean finished;
    private boolean syncScheduled;

    TradeSession(Prueba plugin, TradeManager manager, Player first, Player second) {
        this.plugin = plugin;
        this.manager = manager;
        this.first = first;
        this.second = second;
        this.firstView = createView(first, second);
        this.secondView = createView(second, first);
    }

    UUID firstId() {
        return first.getUniqueId();
    }

    UUID secondId() {
        return second.getUniqueId();
    }

    private Inventory createView(Player owner, Player partner) {
        Holder holder = new Holder(this, owner.getUniqueId());
        Inventory view = Bukkit.createInventory(holder, 54,
                Messages.component("trade.gui.title", "player", partner.getName()));
        holder.inventory = view;
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        for (int row = 0; row < 4; row++) {
            view.setItem(row * 9 + 4, filler);
        }
        for (int slot = 36; slot < 54; slot++) {
            view.setItem(slot, filler);
        }
        return view;
    }

    void open() {
        paintControls();
        first.openInventory(firstView);
        second.openInventory(secondView);
        tellBoth("trade.instructions");
    }

    void click(InventoryClickEvent event, Player who) {
        if (finished) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == top) {
            int slot = event.getSlot();
            if (slot == CONFIRM_SLOT) {
                event.setCancelled(true);
                toggleConfirmation(who);
                return;
            }
            if (!IS_OWN[slot]) {
                event.setCancelled(true);
                return;
            }
            scheduleSync();
            return;
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) {
                return;
            }
            int amount = item.getAmount();
            ItemStack leftover = pushIntoOffer(top, item.clone());
            if (leftover != null && leftover.getAmount() == amount) {
                Messages.send(who, "trade.offer-full");
                return;
            }
            event.setCurrentItem(leftover);
            scheduleSync();
        }
    }

    private ItemStack pushIntoOffer(Inventory view, ItemStack item) {
        for (int slot : OWN_SLOTS) {
            ItemStack current = view.getItem(slot);
            if (current == null || !current.isSimilar(item) || current.getAmount() >= current.getMaxStackSize()) {
                continue;
            }
            int moved = Math.min(item.getAmount(), current.getMaxStackSize() - current.getAmount());
            current.setAmount(current.getAmount() + moved);
            item.setAmount(item.getAmount() - moved);
            if (item.getAmount() <= 0) {
                return null;
            }
        }
        for (int slot : OWN_SLOTS) {
            ItemStack current = view.getItem(slot);
            if (current == null || current.getType().isAir()) {
                view.setItem(slot, item);
                return null;
            }
        }
        return item;
    }

    void drag(InventoryDragEvent event) {
        if (finished) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < 54 && !IS_OWN[raw]) {
                event.setCancelled(true);
                return;
            }
        }
        scheduleSync();
    }

    private void scheduleSync() {
        if (syncScheduled || finished) {
            return;
        }
        syncScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncScheduled = false;
            sync();
        });
    }

    private void sync() {
        if (finished) {
            return;
        }
        boolean changed = readOffer(firstView, firstOffer) | readOffer(secondView, secondOffer);
        if (changed && (firstReady || secondReady)) {
            firstReady = false;
            secondReady = false;
            tellBoth("trade.offer-changed");
        }
        mirror(firstView, secondOffer);
        mirror(secondView, firstOffer);
        paintControls();
    }

    private boolean readOffer(Inventory view, ItemStack[] offer) {
        boolean changed = false;
        for (int i = 0; i < OWN_SLOTS.length; i++) {
            ItemStack current = view.getItem(OWN_SLOTS[i]);
            if (current != null && current.getType().isAir()) {
                current = null;
            }
            if (!Objects.equals(current, offer[i])) {
                offer[i] = current == null ? null : current.clone();
                changed = true;
            }
        }
        return changed;
    }

    private void mirror(Inventory view, ItemStack[] offer) {
        for (int i = 0; i < MIRROR_SLOTS.length; i++) {
            view.setItem(MIRROR_SLOTS[i], offer[i] == null ? null : offer[i].clone());
        }
    }

    private void toggleConfirmation(Player who) {
        sync();
        if (who.getUniqueId().equals(first.getUniqueId())) {
            firstReady = !firstReady;
        } else {
            secondReady = !secondReady;
        }
        if (firstReady && secondReady) {
            complete();
            return;
        }
        paintControls();
    }

    private void complete() {
        if (freeSlots(first) < countItems(secondOffer) || freeSlots(second) < countItems(firstOffer)) {
            firstReady = false;
            secondReady = false;
            tellBoth("trade.no-space");
            paintControls();
            return;
        }
        finished = true;
        manager.forget(this);
        clearOfferSlots();
        deliver(first, secondOffer);
        deliver(second, firstOffer);
        closeViews();
        tellBoth("trade.completed");
        play(Sound.ENTITY_VILLAGER_YES);
    }

    void cancel(String reason) {
        if (finished) {
            return;
        }
        finished = true;
        manager.forget(this);
        readOffer(firstView, firstOffer);
        readOffer(secondView, secondOffer);
        clearOfferSlots();
        deliver(first, firstOffer);
        deliver(second, secondOffer);
        closeViews();
        tellBothRaw(reason);
        play(Sound.ENTITY_VILLAGER_NO);
    }

    void onViewClosed() {
        if (finished) {
            return;
        }
        cancel(Messages.raw("trade.cancelled"));
    }

    private void clearOfferSlots() {
        for (int slot : OWN_SLOTS) {
            firstView.setItem(slot, null);
            secondView.setItem(slot, null);
        }
    }

    private void deliver(Player player, ItemStack[] offer) {
        for (ItemStack item : offer) {
            if (item == null) {
                continue;
            }
            if (player.isOnline()) {
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    private void closeViews() {
        Runnable close = () -> {
            closeIfMine(first);
            closeIfMine(second);
        };
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, close);
        } else {
            close.run();
        }
    }

    private void closeIfMine(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                && holder.session == this) {
            player.closeInventory();
        }
    }

    private int freeSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    private int countItems(ItemStack[] offer) {
        int total = 0;
        for (ItemStack item : offer) {
            if (item != null) {
                total++;
            }
        }
        return total;
    }

    private void paintControls() {
        firstView.setItem(CONFIRM_SLOT, confirmButton(firstReady));
        firstView.setItem(PARTNER_SLOT, partnerLamp(second.getName(), secondReady));
        firstView.setItem(SUMMARY_SLOT, summary(firstOffer, secondOffer));
        secondView.setItem(CONFIRM_SLOT, confirmButton(secondReady));
        secondView.setItem(PARTNER_SLOT, partnerLamp(first.getName(), firstReady));
        secondView.setItem(SUMMARY_SLOT, summary(secondOffer, firstOffer));
    }

    private ItemStack confirmButton(boolean ready) {
        ItemStack button = new ItemStack(ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE);
        button.editMeta(meta -> {
            meta.displayName(plain(Messages.raw(ready ? "trade.gui.confirmed" : "trade.gui.confirm")));
            meta.lore(List.of(plain(Messages.raw(ready ? "trade.gui.confirmed-lore" : "trade.gui.confirm-lore"))));
        });
        return button;
    }

    private ItemStack partnerLamp(String name, boolean ready) {
        ItemStack lamp = new ItemStack(ready ? Material.LIME_DYE : Material.GRAY_DYE);
        lamp.editMeta(meta -> meta.displayName(plain(
                Messages.raw(ready ? "trade.gui.partner-ready" : "trade.gui.partner-waiting", "player", name))));
        return lamp;
    }

    private ItemStack summary(ItemStack[] own, ItemStack[] partner) {
        ItemStack paper = new ItemStack(Material.PAPER);
        paper.editMeta(meta -> {
            meta.displayName(plain(Messages.raw("trade.gui.summary")));
            meta.lore(List.of(
                    plain(Messages.raw("trade.gui.summary-give", "amount", String.valueOf(countItems(own)))),
                    plain(Messages.raw("trade.gui.summary-receive", "amount", String.valueOf(countItems(partner))))));
        });
        return paper;
    }

    private Component plain(String text) {
        return Messages.mm(text).decoration(TextDecoration.ITALIC, false);
    }

    private void tellBoth(String key) {
        if (first.isOnline()) {
            Messages.send(first, key);
        }
        if (second.isOnline()) {
            Messages.send(second, key);
        }
    }

    private void tellBothRaw(String text) {
        if (first.isOnline()) {
            first.sendMessage(Messages.mm(Messages.raw("prefix") + text));
        }
        if (second.isOnline()) {
            second.sendMessage(Messages.mm(Messages.raw("prefix") + text));
        }
    }

    private void play(Sound sound) {
        if (first.isOnline()) {
            first.playSound(first.getLocation(), sound, 1f, 1f);
        }
        if (second.isOnline()) {
            second.playSound(second.getLocation(), sound, 1f, 1f);
        }
    }

    static final class Holder implements InventoryHolder {

        final TradeSession session;
        final UUID owner;
        Inventory inventory;

        Holder(TradeSession session, UUID owner) {
            this.session = session;
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
