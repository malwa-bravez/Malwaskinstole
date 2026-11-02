package com.malwa.skinstole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** One active revival at one chair: beacon-style beam, raid mobs, endermen and the countdown. */
public final class RevivalRitual {
    private static final int PERIOD = 5; // ticks between ritual updates

    private final MalwaSkinStole plugin;
    private final Block chair;
    private final World world;
    private final UUID performer;
    private final String performerName;
    private final Items.SkinData skin;
    private final ItemStack skinItem;
    private final Runnable onEnd;
    private final Random rng = new Random();

    private final Set<UUID> raidMobs = new HashSet<>();
    private final Map<UUID, Integer> enders = new HashMap<>(); // uuid -> age in ticks

    private ItemDisplay display;
    private BukkitTask task;
    private int ticks;
    private boolean ended;

    public RevivalRitual(MalwaSkinStole plugin, Block chair, Player performer, Items.SkinData skin,
                         ItemStack skinItem, Runnable onEnd) {
        this.plugin = plugin;
        this.chair = chair;
        this.world = chair.getWorld();
        this.performer = performer.getUniqueId();
        this.performerName = performer.getName();
        this.skin = skin;
        this.skinItem = skinItem;
        this.onEnd = onEnd;
    }

    public UUID performerId() { return performer; }

    private Location top() {
        return chair.getLocation().add(0.5, 1.0, 0.5);
    }

    private int cfgInt(String path, int def) {
        return plugin.getConfig().getInt("revival-chair.ritual." + path, def);
    }

    public void start() {
        // The skin "sits" on the chair as a floating head.
        display = world.spawn(top().add(0, 0.45, 0), ItemDisplay.class, d -> {
            d.setItemStack(skinItem);
            d.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f()));
        });

        announce(Component.text("Revival in progress", NamedTextColor.LIGHT_PURPLE),
                Component.text(performerName + " is reviving " + skin.name(), NamedTextColor.GRAY),
                performerName + " has begun reviving " + skin.name() + " at a Revival Chair ("
                        + chair.getX() + ", " + chair.getY() + ", " + chair.getZ() + ")!");
        world.playSound(top(), Sound.BLOCK_BEACON_ACTIVATE, 4f, 1f);

        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 0L, PERIOD);
    }

    private void tick() {
        if (ended) return;
        Player p = Bukkit.getPlayer(performer);
        int maxDist = cfgInt("max-distance-from-chair", 48);
        if (p == null || p.isDead() || !p.getWorld().equals(world)
                || p.getLocation().distanceSquared(top()) > (double) maxDist * maxDist) {
            fail(p == null ? "The reviver left." : "The reviver fell or strayed too far from the chair.");
            return;
        }
        ticks += PERIOD;
        int total = cfgInt("duration-seconds", 300) * 20;

        drawBeam();
        raidMobs.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });

        if (ticks % (cfgInt("raid-spawn-interval-seconds", 3) * 20) < PERIOD
                && raidMobs.size() < cfgInt("max-raid-mobs", 14)) {
            spawnRaidMob(p);
        }
        if (ticks % (cfgInt("ender-spawn-interval-seconds", 20) * 20) < PERIOD
                && enders.size() < cfgInt("max-endermen", 3)) {
            spawnEnderman();
        }
        tickEndermen(p);

        if (ticks % 20 < PERIOD) {
            int left = (total - ticks) / 20;
            p.sendActionBar(Component.text("Revival: " + left / 60 + "m " + left % 60 + "s remaining — survive!",
                    NamedTextColor.LIGHT_PURPLE));
        }
        if (ticks >= total) succeed(p);
    }

    /** Particle column straight up to build height, visible through blocks. */
    private void drawBeam() {
        double x = chair.getX() + 0.5, z = chair.getZ() + 0.5;
        int maxY = world.getMaxHeight();
        for (int y = chair.getY() + 1; y < maxY; y += 3) {
            world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.08, 1.0, 0.08, 0.0, null, true);
        }
        if (ticks % 40 < PERIOD) {
            world.spawnParticle(Particle.ENCHANTMENT_TABLE, top(), 40, 0.8, 0.8, 0.8, 1.0);
        }
    }

    private void spawnRaidMob(Player target) {
        Location spot = findSpot(target.getLocation(), 8, 14);
        if (spot == null) return;
        Mob mob;
        switch (rng.nextInt(4)) {
            case 0 -> mob = world.spawn(spot, Zombie.class, z -> z.setShouldBurnInDay(false));
            case 1 -> mob = world.spawn(spot, Skeleton.class, s -> s.setShouldBurnInDay(false));
            case 2 -> mob = world.spawn(spot, Creeper.class, c -> { });
            default -> mob = world.spawn(spot, Spider.class, s -> { });
        }
        mob.getPersistentDataContainer().set(Keys.RAID_MOB, PersistentDataType.STRING, performer.toString());
        mob.setRemoveWhenFarAway(false);
        mob.setTarget(target);
        raidMobs.add(mob.getUniqueId());
    }

    private void spawnEnderman() {
        Location spot = findSpot(top(), 6, 9);
        if (spot == null) return;
        Enderman e = world.spawn(spot, Enderman.class, en -> en.setRemoveWhenFarAway(false));
        e.getPersistentDataContainer().set(Keys.RITUAL_ENDER, PersistentDataType.BYTE, (byte) 1);
        enders.put(e.getUniqueId(), 0);
    }

    /** Endermen walk to the chair, reach for the skin and always fail. */
    private void tickEndermen(Player performerPlayer) {
        Location chairLoc = top();
        for (var it = enders.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Entity ent = Bukkit.getEntity(entry.getKey());
            if (!(ent instanceof Enderman e) || !e.isValid()) {
                it.remove();
                continue;
            }
            entry.setValue(entry.getValue() + PERIOD);
            e.setTarget(null);
            boolean reached = e.getLocation().distanceSquared(chairLoc) < 6.25;
            if (reached || entry.getValue() > 800) {
                if (reached) {
                    world.spawnParticle(Particle.REVERSE_PORTAL, e.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.2);
                    world.playSound(chairLoc, Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.7f);
                    performerPlayer.sendActionBar(Component.text(
                            "An Enderman reached for the skin — and was repelled!", NamedTextColor.DARK_PURPLE));
                }
                e.remove();
                it.remove();
            } else {
                e.getPathfinder().moveTo(chairLoc, 1.3);
            }
        }
    }

    /** Finds an open standing spot in a ring around the centre, or null. */
    private Location findSpot(Location center, int minR, int maxR) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = minR + rng.nextDouble() * (maxR - minR);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int y = center.getBlockY() + 4; y >= center.getBlockY() - 6; y--) {
                Block feet = world.getBlockAt(x, y, z);
                Block ground = feet.getRelative(0, -1, 0);
                if (!ground.isPassable() && !ground.isLiquid()
                        && feet.isPassable() && !feet.isLiquid()
                        && feet.getRelative(0, 1, 0).isPassable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    private void succeed(Player p) {
        String name = skin.name();
        cleanup();
        plugin.unbanPlayer(name);
        world.playSound(top(), Sound.ITEM_TOTEM_USE, 4f, 1f);
        world.spawnParticle(Particle.TOTEM, top(), 200, 1, 2, 1, 0.5);
        announce(Component.text("Revival complete", NamedTextColor.GOLD),
                Component.text(name + " has returned!", NamedTextColor.YELLOW),
                name + " has been revived by " + performerName + "!");
        p.sendMessage(Component.text("The ritual succeeded. " + name + " is unbanned and may rejoin.",
                NamedTextColor.GREEN));
    }

    /** Aborts the ritual and returns the skin to the chair. */
    public void fail(String reason) {
        if (ended) return;
        Location drop = top();
        cleanup();
        world.dropItemNaturally(drop, skinItem);
        world.playSound(drop, Sound.BLOCK_BEACON_DEACTIVATE, 4f, 1f);
        Player p = Bukkit.getPlayer(performer);
        if (p != null) {
            p.sendMessage(Component.text("The revival failed: " + reason, NamedTextColor.RED));
        }
    }

    private void cleanup() {
        ended = true;
        if (task != null) task.cancel();
        if (display != null && display.isValid()) display.remove();
        for (UUID id : raidMobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        for (UUID id : enders.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        raidMobs.clear();
        enders.clear();
        onEnd.run();
    }

    private void announce(Component title, Component subtitle, String chat) {
        int radius = cfgInt("notify-radius", 1000);
        double r2 = (double) radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(top()) <= r2) {
                p.showTitle(Title.title(title, subtitle));
                p.sendMessage(Component.text(chat, NamedTextColor.LIGHT_PURPLE));
            }
        }
    }
}
