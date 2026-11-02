package com.malwa.skinstole;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks every Revival Chair. The index of locations lives in chairs.yml (needed for the
 * world-wide limit and /s locate); owner registration lives in the chunk's PersistentDataContainer.
 */
public final class ChairManager {
    public record ChairPos(String world, int x, int y, int z) {
        String id() { return world + ";" + x + ";" + y + ";" + z; }
        static ChairPos of(Block b) { return new ChairPos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
    }

    private final MalwaSkinStole plugin;
    private final File file;
    private final Set<ChairPos> chairs = new LinkedHashSet<>();
    private final Map<ChairPos, RevivalRitual> rituals = new HashMap<>();

    public ChairManager(MalwaSkinStole plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chairs.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String s : yaml.getStringList("chairs")) {
            String[] p = s.split(";");
            if (p.length != 4) continue;
            try {
                chairs.add(new ChairPos(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (ChairPos c : chairs) list.add(c.id());
        yaml.set("chairs", list);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save chairs.yml: " + ex.getMessage());
        }
    }

    public boolean isChair(Block b) {
        return chairs.contains(ChairPos.of(b));
    }

    public int count() {
        return chairs.size();
    }

    public void add(Block b) {
        chairs.add(ChairPos.of(b));
        setOwner(b, null);
        save();
    }

    public void remove(Block b) {
        chairs.remove(ChairPos.of(b));
        b.getChunk().getPersistentDataContainer().remove(Keys.chair(b.getX(), b.getY(), b.getZ()));
        save();
    }

    public UUID getOwner(Block b) {
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        String v = pdc.get(Keys.chair(b.getX(), b.getY(), b.getZ()), PersistentDataType.STRING);
        if (v == null || v.isEmpty()) return null;
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void setOwner(Block b, UUID owner) {
        b.getChunk().getPersistentDataContainer().set(Keys.chair(b.getX(), b.getY(), b.getZ()),
                PersistentDataType.STRING, owner == null ? "" : owner.toString());
    }

    /** Nearest chair in the given location's world, or null. */
    public Location nearest(Location from) {
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (ChairPos c : chairs) {
            if (!c.world().equals(from.getWorld().getName())) continue;
            double d = from.distanceSquared(new Location(from.getWorld(), c.x(), c.y(), c.z()));
            if (d < bestDist) {
                bestDist = d;
                best = new Location(from.getWorld(), c.x(), c.y(), c.z());
            }
        }
        return best;
    }

    /** First chair anywhere (used when the console asks for a location). */
    public Location any() {
        for (ChairPos c : chairs) {
            World w = Bukkit.getWorld(c.world());
            if (w != null) return new Location(w, c.x(), c.y(), c.z());
        }
        return null;
    }

    public RevivalRitual getRitual(Block b) {
        return rituals.get(ChairPos.of(b));
    }

    public boolean startRitual(Player performer, Block chair, ItemStack skinItem) {
        Items.SkinData skin = Items.skinData(skinItem);
        if (skin == null) return false;
        ChairPos pos = ChairPos.of(chair);
        if (rituals.containsKey(pos)) return false;
        RevivalRitual ritual = new RevivalRitual(plugin, chair, performer, skin, skinItem.asOne(), () -> rituals.remove(pos));
        rituals.put(pos, ritual);
        ritual.start();
        return true;
    }

    public RevivalRitual ritualOf(UUID performer) {
        for (RevivalRitual r : rituals.values()) {
            if (r.performerId().equals(performer)) return r;
        }
        return null;
    }

    public void shutdown() {
        for (RevivalRitual r : new ArrayList<>(rituals.values())) r.fail("The server is restarting.");
        rituals.clear();
    }
}
