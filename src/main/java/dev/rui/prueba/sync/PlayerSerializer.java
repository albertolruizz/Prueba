package dev.rui.prueba.sync;

import dev.rui.prueba.config.Settings;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PlayerSerializer {

    private static final Map<String, Statistic> STATISTICS = new LinkedHashMap<>();

    static {
        STATISTICS.put("deaths", Statistic.DEATHS);
        STATISTICS.put("player_kills", Statistic.PLAYER_KILLS);
        STATISTICS.put("mob_kills", Statistic.MOB_KILLS);
        STATISTICS.put("jumps", Statistic.JUMP);
        STATISTICS.put("damage_dealt", Statistic.DAMAGE_DEALT);
        STATISTICS.put("damage_taken", Statistic.DAMAGE_TAKEN);
        STATISTICS.put("fish_caught", Statistic.FISH_CAUGHT);
        STATISTICS.put("ticks_played", Statistic.PLAY_ONE_MINUTE);
    }

    private PlayerSerializer() {
    }

    public static Map<String, Object> capture(Player player, Settings settings) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", player.getName());
        if (settings.field("profile")) {
            fields.put("profile", profile(player, settings));
        }
        if (settings.field("state")) {
            fields.put("state", state(player));
        }
        if (settings.field("locations")) {
            fields.put("locations." + settings.server, location(player.getLocation()));
        }
        if (settings.field("inventory")) {
            fields.put("inventory", inventory(player));
        }
        if (settings.field("statistics")) {
            fields.put("statistics", statistics(player));
        }
        return fields;
    }

    public static void apply(Player player, Document data, Settings settings) {
        if (settings.field("state")) {
            applyState(player, data.get("state", Document.class));
        }
        if (settings.field("inventory")) {
            applyInventory(player, data.get("inventory", Document.class));
        }
        if (settings.field("statistics")) {
            applyStatistics(player, data.get("statistics", Document.class));
        }
        if (settings.field("locations")) {
            applyLocation(player, data.get("locations", Document.class), settings.server);
        }
    }

    private static Document profile(Player player, Settings settings) {
        return new Document("name", player.getName())
                .append("last_seen", new Date())
                .append("last_server", settings.server);
    }

    private static Document state(Player player) {
        List<Document> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(new Document("type", effect.getType().getKey().toString())
                    .append("duration", effect.getDuration())
                    .append("amplifier", effect.getAmplifier())
                    .append("ambient", effect.isAmbient())
                    .append("particles", effect.hasParticles())
                    .append("icon", effect.hasIcon()));
        }
        return new Document("health", player.getHealth())
                .append("food", player.getFoodLevel())
                .append("saturation", (double) player.getSaturation())
                .append("exhaustion", (double) player.getExhaustion())
                .append("level", player.getLevel())
                .append("exp", (double) player.getExp())
                .append("gamemode", player.getGameMode().name())
                .append("fire_ticks", player.getFireTicks())
                .append("air", player.getRemainingAir())
                .append("effects", effects);
    }

    private static Document location(Location point) {
        return new Document("world", point.getWorld().getName())
                .append("x", point.getX())
                .append("y", point.getY())
                .append("z", point.getZ())
                .append("yaw", (double) point.getYaw())
                .append("pitch", (double) point.getPitch());
    }

    private static Document inventory(Player player) {
        String contents = Base64.getEncoder().encodeToString(
                ItemStack.serializeItemsAsBytes(player.getInventory().getContents()));
        String enderChest = Base64.getEncoder().encodeToString(
                ItemStack.serializeItemsAsBytes(player.getEnderChest().getContents()));
        return new Document("contents", contents)
                .append("ender_chest", enderChest)
                .append("held_slot", player.getInventory().getHeldItemSlot());
    }

    private static Document statistics(Player player) {
        Document data = new Document();
        STATISTICS.forEach((key, statistic) -> data.append(key, player.getStatistic(statistic)));
        return data;
    }

    private static void applyState(Player player, Document state) {
        if (state == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = attribute != null ? attribute.getValue() : 20.0;
        Double health = state.getDouble("health");
        if (health != null && health > 0) {
            player.setHealth(Math.min(health, cap));
        }
        Integer food = state.getInteger("food");
        if (food != null) {
            player.setFoodLevel(food);
        }
        Double saturation = state.getDouble("saturation");
        if (saturation != null) {
            player.setSaturation(saturation.floatValue());
        }
        Double exhaustion = state.getDouble("exhaustion");
        if (exhaustion != null) {
            player.setExhaustion(exhaustion.floatValue());
        }
        Integer level = state.getInteger("level");
        if (level != null) {
            player.setLevel(level);
        }
        Double exp = state.getDouble("exp");
        if (exp != null) {
            player.setExp(exp.floatValue());
        }
        String gamemode = state.getString("gamemode");
        if (gamemode != null) {
            try {
                player.setGameMode(GameMode.valueOf(gamemode));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Integer fireTicks = state.getInteger("fire_ticks");
        if (fireTicks != null) {
            player.setFireTicks(fireTicks);
        }
        Integer air = state.getInteger("air");
        if (air != null) {
            player.setRemainingAir(air);
        }
        List<Document> effects = state.getList("effects", Document.class);
        if (effects != null) {
            for (PotionEffect active : player.getActivePotionEffects()) {
                player.removePotionEffect(active.getType());
            }
            for (Document effect : effects) {
                NamespacedKey key = NamespacedKey.fromString(effect.getString("type"));
                if (key == null) {
                    continue;
                }
                PotionEffectType type = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.MOB_EFFECT).get(key);
                if (type == null) {
                    continue;
                }
                player.addPotionEffect(new PotionEffect(type,
                        effect.getInteger("duration", 0),
                        effect.getInteger("amplifier", 0),
                        Boolean.TRUE.equals(effect.getBoolean("ambient")),
                        !Boolean.FALSE.equals(effect.getBoolean("particles")),
                        !Boolean.FALSE.equals(effect.getBoolean("icon"))));
            }
        }
    }

    private static void applyInventory(Player player, Document inventory) {
        if (inventory == null) {
            return;
        }
        String contents = inventory.getString("contents");
        if (contents != null) {
            player.getInventory().setContents(
                    ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(contents)));
        }
        String enderChest = inventory.getString("ender_chest");
        if (enderChest != null) {
            player.getEnderChest().setContents(
                    ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(enderChest)));
        }
        Integer heldSlot = inventory.getInteger("held_slot");
        if (heldSlot != null && heldSlot >= 0 && heldSlot <= 8) {
            player.getInventory().setHeldItemSlot(heldSlot);
        }
    }

    private static void applyStatistics(Player player, Document statistics) {
        if (statistics == null) {
            return;
        }
        STATISTICS.forEach((key, statistic) -> {
            Integer value = statistics.getInteger(key);
            if (value != null) {
                player.setStatistic(statistic, value);
            }
        });
    }

    private static void applyLocation(Player player, Document locations, String server) {
        if (locations == null) {
            return;
        }
        Document point = locations.get(server, Document.class);
        if (point == null) {
            return;
        }
        World world = Bukkit.getWorld(point.getString("world"));
        if (world == null) {
            return;
        }
        player.teleportAsync(new Location(world,
                point.getDouble("x"),
                point.getDouble("y"),
                point.getDouble("z"),
                point.getDouble("yaw").floatValue(),
                point.getDouble("pitch").floatValue()));
    }
}
