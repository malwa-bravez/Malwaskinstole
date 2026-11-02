package com.malwa.skinstole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** The Wizard Warden boss: stats, potion quirks, minion waves, laser and loot. */
public final class WizardWardenManager implements Listener {
    private static final PotionEffectType SLOWNESS = PotionEffectType.getByKey(NamespacedKey.minecraft("slowness"));
    private static final Set<Material> UNBREAKABLE = EnumSet.of(Material.BEDROCK, Material.BARRIER,
            Material.END_PORTAL, Material.END_PORTAL_FRAME, Material.END_GATEWAY, Material.STRUCTURE_BLOCK,
            Material.JIGSAW, Material.REINFORCED_DEEPSLATE, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK);

    private final MalwaSkinStole plugin;
    private final Random rng = new Random();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, BukkitTask> regenTasks = new HashMap<>();
    private final Set<UUID> charging = new HashSet<>();
    private final Set<BukkitTask> laserTasks = new HashSet<>();
    private final BukkitTask barTask;

    public WizardWardenManager(MalwaSkinStole plugin) {
        this.plugin = plugin;
        for (World w : Bukkit.getWorlds()) {
            for (Warden warden : w.getEntitiesByClass(Warden.class)) {
                if (isWizard(warden)) registerBar(warden);
            }
        }
        this.barTask = new BukkitRunnable() {
            @Override public void run() { updateBars(); }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    // ---------------------------------------------------------------- helpers

    private double cfg(String path, double def) {
        return plugin.getConfig().getDouble("wizard-warden." + path, def);
    }

    private int cfgInt(String path, int def) {
        return plugin.getConfig().getInt("wizard-warden." + path, def);
    }

    public boolean isWizard(Entity e) {
        return e instanceof Warden && e.getPersistentDataContainer().has(Keys.WIZARD, PersistentDataType.BYTE);
    }

    private boolean isMinion(Entity e) {
        return e.getPersistentDataContainer().has(Keys.WIZARD_MINION, PersistentDataType.BYTE);
    }

    private double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return a == null ? e.getHealth() : a.getValue();
    }

    private void setMaxHealth(LivingEntity e, double value) {
        AttributeInstance a = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (a != null) a.setBaseValue(value);
    }

    // ------------------------------------------------------------------ spawn

    public Warden spawn(Location loc) {
        double hp = cfg("health", 1150);
        Warden w = loc.getWorld().spawn(loc, Warden.class, ww -> {
            setMaxHealth(ww, hp);
            ww.setHealth(hp);
            ww.customName(Component.text("Wizard Warden", NamedTextColor.DARK_PURPLE));
            ww.setCustomNameVisible(true);
            ww.setPersistent(true);
            ww.setRemoveWhenFarAway(false);
            ww.getPersistentDataContainer().set(Keys.WIZARD, PersistentDataType.BYTE, (byte) 1);
        });
        registerBar(w);
        return w;
    }

    /**
     * Finds the nearest Ancient City, carves a sealed hidden chamber inside it and spawns the boss there.
     * Returns the spawn location, or null (with a reason sent to the player) if no city was found.
     */
    public Location spawnInAncientCity(Player requester) {
        World world = requester.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            requester.sendMessage(Component.text("Ancient Cities only exist in the Overworld.", NamedTextColor.RED));
            return null;
        }
        int radius = cfgInt("search-radius-chunks", 100);
        Location city = world.locateNearestStructure(requester.getLocation(), Structure.ANCIENT_CITY, radius, false);
        if (city == null) {
            requester.sendMessage(Component.text("No Ancient City found within " + radius + " chunks.", NamedTextColor.RED));
            return null;
        }
        int cx = city.getBlockX(), cz = city.getBlockZ();
        int floorY = cfgInt("room-y", -50);
        carveRoom(world, cx, floorY, cz);
        Location spawn = new Location(world, cx + 0.5, floorY + 1, cz + 0.5);
        spawn(spawn);
        return spawn;
    }

    /** A sealed 13x13x8 deepslate chamber with a sculk floor and soul-lantern light. */
    private void carveRoom(World world, int cx, int floorY, int cz) {
        int r = 6, h = 8;
        for (int x = cx - r; x <= cx + r; x += 16) for (int z = cz - r; z <= cz + r; z += 16) world.getChunkAt(x >> 4, z >> 4).load(true);
        world.getChunkAt((cx + r) >> 4, (cz + r) >> 4).load(true);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= h; dy++) {
                    boolean wall = Math.abs(dx) == r || Math.abs(dz) == r;
                    Material m;
                    if (dy == 0) m = (dx + dz) % 2 == 0 ? Material.SCULK : Material.DEEPSLATE_TILES;
                    else if (dy == h || wall) m = Material.DEEPSLATE_BRICKS;
                    else m = Material.AIR;
                    world.getBlockAt(cx + dx, floorY + dy, cz + dz).setType(m, false);
                }
            }
        }
        for (int sx : new int[]{-r + 1, r - 1}) {
            for (int sz : new int[]{-r + 1, r - 1}) {
                world.getBlockAt(cx + sx, floorY + 1, cz + sz).setType(Material.SOUL_LANTERN, false);
            }
        }
        world.getBlockAt(cx, floorY, cz).setType(Material.CHISELED_DEEPSLATE, false);
    }

    // -------------------------------------------------------------- boss bars

    private void registerBar(Warden w) {
        bars.computeIfAbsent(w.getUniqueId(), id ->
                Bukkit.createBossBar("Wizard Warden", BarColor.PURPLE, BarStyle.SEGMENTED_10));
    }

    private void updateBars() {
        for (var entry : new HashMap<>(bars).entrySet()) {
            Entity e = Bukkit.getEntity(entry.getKey());
            BossBar bar = entry.getValue();
            if (!(e instanceof LivingEntity le) || !le.isValid()) {
                bar.removeAll();
                continue;
            }
            bar.setProgress(Math.max(0, Math.min(1, le.getHealth() / maxHealth(le))));
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean near = p.getWorld().equals(le.getWorld()) && p.getLocation().distanceSquared(le.getLocation()) < 64 * 64;
                if (near) bar.addPlayer(p); else bar.removePlayer(p);
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity ent : e.getEntities()) {
            if (isWizard(ent)) registerBar((Warden) ent);
        }
    }

    // ----------------------------------------------------------------- combat

    /** Slowness or Weakness on the boss cuts the damage it deals. */
    @EventHandler(ignoreCancelled = true)
    public void onWizardAttacks(EntityDamageByEntityEvent e) {
        if (!isWizard(e.getDamager())) return;
        LivingEntity w = (LivingEntity) e.getDamager();
        boolean weakened = w.hasPotionEffect(PotionEffectType.WEAKNESS)
                || (SLOWNESS != null && w.hasPotionEffect(SLOWNESS));
        if (weakened) e.setDamage(e.getDamage() * cfg("weakened-damage-multiplier", 0.25));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWizardDamaged(EntityDamageEvent e) {
        if (!isWizard(e.getEntity())) return;
        Warden w = (Warden) e.getEntity();
        double after = Math.max(0, w.getHealth() - e.getFinalDamage());
        if (after <= 0) return;

        // Minion waves: every N HP lost from base health.
        double lost = cfg("health", 1150) - after;
        int milestones = (int) (lost / cfg("minion-every-hp-lost", 400));
        int fired = w.getPersistentDataContainer().getOrDefault(Keys.WIZARD_MILESTONES, PersistentDataType.INTEGER, 0);
        while (fired < milestones) {
            fired++;
            w.getPersistentDataContainer().set(Keys.WIZARD_MILESTONES, PersistentDataType.INTEGER, fired);
            spawnMinions(w);
        }

        // Laser phase at 29% of base health, once per fight.
        double threshold = cfg("health", 1150) * cfg("laser.trigger-percent", 0.29);
        if (after <= threshold && !w.getPersistentDataContainer().has(Keys.WIZARD_LASER, PersistentDataType.BYTE)) {
            w.getPersistentDataContainer().set(Keys.WIZARD_LASER, PersistentDataType.BYTE, (byte) 1);
            Bukkit.getScheduler().runTask(plugin, () -> startLaser(w));
        }
    }

    /** Regeneration permanently lowers max health and makes it bleed per regen tick. */
    @EventHandler
    public void onPotion(EntityPotionEffectEvent e) {
        if (!isWizard(e.getEntity()) || e.getNewEffect() == null) return;
        if (e.getNewEffect().getType() != PotionEffectType.REGENERATION) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        Warden w = (Warden) e.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> startRegenDrain(w));
    }

    private void startRegenDrain(Warden w) {
        double cap = cfg("regen-max-health", 780);
        if (maxHealth(w) > cap) {
            setMaxHealth(w, cap);
            if (w.getHealth() > cap) w.setHealth(cap);
        }
        if (regenTasks.containsKey(w.getUniqueId())) return;
        PotionEffect eff = w.getPotionEffect(PotionEffectType.REGENERATION);
        int amp = eff == null ? 0 : eff.getAmplifier();
        long interval = Math.max(1, 50 >> Math.min(amp, 5)); // vanilla regeneration heal interval
        int min = cfgInt("regen-drain-min", 20), max = cfgInt("regen-drain-max", 30);
        BukkitTask t = new BukkitRunnable() {
            @Override public void run() {
                if (!w.isValid() || !w.hasPotionEffect(PotionEffectType.REGENERATION)) {
                    regenTasks.remove(w.getUniqueId());
                    cancel();
                    return;
                }
                w.damage(min + rng.nextInt(Math.max(1, max - min + 1)));
            }
        }.runTaskTimer(plugin, interval, interval);
        regenTasks.put(w.getUniqueId(), t);
    }

    private void spawnMinions(Warden boss) {
        int count = cfgInt("minions-per-wave", 2);
        int minTicks = cfgInt("minion-min-seconds", 120) * 20;
        int maxTicks = cfgInt("minion-max-seconds", 240) * 20;
        for (int i = 0; i < count; i++) {
            Location l = boss.getLocation().add(rng.nextInt(9) - 4, 0, rng.nextInt(9) - 4);
            Warden m = boss.getWorld().spawn(l, Warden.class, mm -> {
                mm.customName(Component.text("Wizard's Minion", NamedTextColor.DARK_GRAY));
                mm.setPersistent(false);
                mm.getPersistentDataContainer().set(Keys.WIZARD_MINION, PersistentDataType.BYTE, (byte) 1);
            });
            for (Player p : boss.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(boss.getLocation()) < 48 * 48) m.setAnger(p, 80);
            }
            long life = minTicks + rng.nextInt(Math.max(1, maxTicks - minTicks));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.isValid()) {
                    m.getWorld().spawnParticle(Particle.SMOKE_NORMAL, m.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.02);
                    m.remove();
                }
            }, life);
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 4f, 0.8f);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Entity ent = e.getEntity();
        if (isMinion(ent)) {
            e.getDrops().clear();
            return;
        }
        if (!isWizard(ent)) return;
        e.getDrops().clear();
        int min = cfgInt("loot-min", 2), max = cfgInt("loot-max", 5);
        e.getDrops().add(Items.token(min + rng.nextInt(Math.max(1, max - min + 1))));
        BossBar bar = bars.remove(ent.getUniqueId());
        if (bar != null) bar.removeAll();
        BukkitTask t = regenTasks.remove(ent.getUniqueId());
        if (t != null) t.cancel();
        charging.remove(ent.getUniqueId());
        Bukkit.broadcast(Component.text("The Wizard Warden has fallen!", NamedTextColor.DARK_PURPLE));
    }

    // ------------------------------------------------------------------ laser

    private void startLaser(Warden w) {
        if (!w.isValid()) return;
        UUID id = w.getUniqueId();
        charging.add(id);
        w.setAI(false);
        w.getWorld().playSound(w.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 6f, 1f);
        int chargeTicks = cfgInt("laser.charge-ticks", 60);

        BukkitTask[] self = new BukkitTask[1];
        self[0] = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!w.isValid()) {
                    charging.remove(id);
                    laserTasks.remove(self[0]);
                    cancel();
                    return;
                }
                t += 2;
                Location eye = w.getEyeLocation();
                double radius = 1.5 + 0.5 * Math.sin(t * 0.4);
                for (int i = 0; i < 12; i++) {
                    double a = t * 0.5 + i * (Math.PI / 6);
                    Location p = eye.clone().add(Math.cos(a) * radius, Math.sin(a * 2) * 0.6, Math.sin(a) * radius);
                    Color c = i % 2 == 0 ? Color.RED : Color.fromRGB(255, 200, 40);
                    w.getWorld().spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 2f), true);
                }
                if (t >= chargeTicks) {
                    cancel();
                    laserTasks.remove(self[0]);
                    fireLaser(w);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        laserTasks.add(self[0]);
    }

    private Player nearestPlayer(Warden w) {
        Player best = null;
        double bd = 80 * 80;
        for (Player p : w.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(w.getLocation());
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** 100-long, 30-wide beam that mines every breakable block it passes through, sliced across ticks. */
    private void fireLaser(Warden w) {
        UUID id = w.getUniqueId();
        World world = w.getWorld();
        Location origin = w.getEyeLocation();
        Player target = nearestPlayer(w);
        Vector dir = target != null
                ? target.getEyeLocation().toVector().subtract(origin.toVector())
                : w.getLocation().getDirection();
        if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
        dir.normalize();
        final Vector d = dir;

        Vector helper = Math.abs(d.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        final Vector right = d.clone().crossProduct(helper).normalize();
        final Vector up = right.clone().crossProduct(d).normalize();

        final int length = cfgInt("laser.length", 100);
        final int radius = cfgInt("laser.width", 30) / 2;
        final int perTick = Math.max(1, cfgInt("laser.slices-per-tick", 3));
        final boolean destroy = plugin.getConfig().getBoolean("wizard-warden.laser.destroy-blocks", true);
        final double damage = cfg("laser.damage", 40.0);
        final Set<UUID> hit = new HashSet<>();

        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 8f, 0.5f);

        BukkitTask[] self = new BukkitTask[1];
        self[0] = new BukkitRunnable() {
            int step = 0;
            @Override public void run() {
                for (int s = 0; s < perTick && step <= length; s++, step++) {
                    Vector c = origin.toVector().add(d.clone().multiply(step));
                    Location cl = c.toLocation(world);
                    if (step % 2 == 0) {
                        world.spawnParticle(Particle.REDSTONE, cl, 6, 1.5, 1.5, 1.5, 0,
                                new Particle.DustOptions(step % 4 == 0 ? Color.RED : Color.fromRGB(255, 200, 40), 4f), true);
                    }
                    if (destroy) mineSlice(world, c, right, up, radius);
                    damageSlice(world, w, cl, d, radius, damage, hit);
                }
                if (step > length) {
                    cancel();
                    laserTasks.remove(self[0]);
                    charging.remove(id);
                    if (w.isValid()) w.setAI(true);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        laserTasks.add(self[0]);
    }

    private void mineSlice(World world, Vector c, Vector right, Vector up, int radius) {
        int r2 = radius * radius;
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                if (u * u + v * v > r2) continue;
                int x = (int) Math.floor(c.getX() + right.getX() * u + up.getX() * v);
                int y = (int) Math.floor(c.getY() + right.getY() * u + up.getY() * v);
                int z = (int) Math.floor(c.getZ() + right.getZ() * u + up.getZ() * v);
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                Block b = world.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m.isAir() || UNBREAKABLE.contains(m) || plugin.chairs().isChair(b)) continue;
                b.setType(Material.AIR, false);
            }
        }
    }

    private void damageSlice(World world, Warden source, Location center, Vector dir, int radius,
                             double damage, Set<UUID> hit) {
        for (Entity ent : world.getNearbyEntities(center, radius + 1, radius + 1, radius + 1)) {
            if (!(ent instanceof LivingEntity le) || ent.equals(source) || ent instanceof Warden) continue;
            if (hit.contains(ent.getUniqueId())) continue;
            Vector rel = le.getLocation().toVector().subtract(center.toVector());
            double along = rel.dot(dir);
            if (Math.abs(along) > 2.5) continue;
            double perp = rel.clone().subtract(dir.clone().multiply(along)).length();
            if (perp <= radius) {
                hit.add(ent.getUniqueId());
                le.damage(damage, source);
            }
        }
    }

    // --------------------------------------------------------------- shutdown

    public void shutdown() {
        barTask.cancel();
        laserTasks.forEach(BukkitTask::cancel);
        regenTasks.values().forEach(BukkitTask::cancel);
        for (UUID id : charging) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof Warden w) w.setAI(true);
        }
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
    }
}
