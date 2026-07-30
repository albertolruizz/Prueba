package dev.rui.prueba.proxy;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.rui.prueba.Prueba;
import org.bukkit.entity.Player;

public final class ProxyBridge {

    public static final String CHANNEL = "BungeeCord";

    private final Prueba plugin;

    public ProxyBridge(Prueba plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public boolean send(Player player, String server) {
        if (!plugin.settings().proxyEnabled) {
            return false;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        return true;
    }
}
