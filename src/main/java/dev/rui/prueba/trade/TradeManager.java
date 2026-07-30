package dev.rui.prueba.trade;

import dev.rui.prueba.Prueba;
import dev.rui.prueba.sync.SyncService;
import dev.rui.prueba.util.Messages;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TradeManager implements Listener {

    private final Prueba plugin;
    private final SyncService sync;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();

    private record Request(UUID sender, long expiresAt) {
    }

    public TradeManager(Prueba plugin, SyncService sync) {
        this.plugin = plugin;
        this.sync = sync;
    }

    public void request(Player sender, Player target) {
        if (!plugin.settings().tradeEnabled) {
            Messages.send(sender, "trade.disabled");
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            Messages.send(sender, "trade.self");
            return;
        }
        if (!available(sender) || !available(target)) {
            Messages.send(sender, "trade.unavailable");
            return;
        }
        if (!nearby(sender, target)) {
            Messages.send(sender, "trade.too-far", "range", String.valueOf((int) plugin.settings().tradeRange));
            return;
        }
        int seconds = plugin.settings().tradeRequestSeconds;
        requests.put(target.getUniqueId(),
                new Request(sender.getUniqueId(), System.currentTimeMillis() + seconds * 1000L));
        Messages.send(sender, "trade.request-sent", "player", target.getName());
        Messages.send(target, "trade.request-received",
                "player", sender.getName(), "seconds", String.valueOf(seconds));
    }

    public void accept(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            Messages.send(target, "trade.no-request");
            return;
        }
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null || !sender.isOnline()) {
            Messages.send(target, "trade.player-offline");
            return;
        }
        if (!available(sender) || !available(target)) {
            Messages.send(target, "trade.unavailable");
            return;
        }
        if (!nearby(sender, target)) {
            Messages.send(target, "trade.too-far", "range", String.valueOf((int) plugin.settings().tradeRange));
            return;
        }
        TradeSession session = new TradeSession(plugin, this, sender, target);
        sessions.put(sender.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);
        session.open();
    }

    public void deny(Player target) {
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expiresAt() < System.currentTimeMillis()) {
            Messages.send(target, "trade.no-request");
            return;
        }
        Messages.send(target, "trade.denied");
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null && sender.isOnline()) {
            Messages.send(sender, "trade.denied-sender", "player", target.getName());
        }
    }

    public void cancel(Player who) {
        TradeSession session = sessions.get(who.getUniqueId());
        if (session != null) {
            session.cancel(Messages.raw("trade.cancelled-by", "player", who.getName()));
            return;
        }
        boolean had = requests.entrySet()
                .removeIf(entry -> entry.getValue().sender().equals(who.getUniqueId()));
        Messages.send(who, had ? "trade.request-withdrawn" : "trade.none-running");
    }

    private boolean available(Player player) {
        return sync.isReady(player.getUniqueId()) && !sessions.containsKey(player.getUniqueId());
    }

    private boolean nearby(Player one, Player other) {
        double range = plugin.settings().tradeRange;
        if (range <= 0) {
            return true;
        }
        return one.getWorld().equals(other.getWorld())
                && one.getLocation().distanceSquared(other.getLocation()) <= range * range;
    }

    void forget(TradeSession session) {
        sessions.remove(session.firstId(), session);
        sessions.remove(session.secondId(), session);
    }

    public void cancelAll() {
        for (TradeSession session : new HashSet<>(sessions.values())) {
            session.cancel(Messages.raw("trade.cancelled-shutdown"));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TradeSession.Holder holder
                && event.getWhoClicked() instanceof Player player) {
            holder.session.click(event, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TradeSession.Holder holder) {
            holder.session.drag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TradeSession.Holder holder) {
            holder.session.onViewClosed();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        requests.remove(uuid);
        requests.entrySet().removeIf(entry -> entry.getValue().sender().equals(uuid));
        TradeSession session = sessions.get(uuid);
        if (session != null) {
            session.cancel(Messages.raw("trade.cancelled-quit"));
        }
    }
}
