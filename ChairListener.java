package com.malwa.skinstole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class ChairListener implements Listener {
    private final MalwaSkinStole plugin;

    public ChairListener(MalwaSkinStole plugin) {
        this.plugin = plugin;
    }

    private ChairManager chairs() { return plugin.chairs(); }

    private static void say(Player p, String msg, NamedTextColor color) {
        p.sendMessage(Component.text(msg, color));
    }

    private static String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString() : n;
    }

    // ------------------------------------------------------------ place / break

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!Items.is(e.getItemInHand(), Items.CHAIR)) return;
        int max = plugin.getConfig().getInt("revival-chair.max-in-world", 7);
        if (chairs().count() >= max) {
            e.setCancelled(true);
            say(e.getPlayer(), "Only " + max + " Revival Chairs can exist in the world at once.", NamedTextColor.RED);
            return;
        }
        chairs().add(e.getBlockPlaced());
        say(e.getPlayer(), "Revival Chair placed. Crouch and right-click it to register it to yourself.",
                NamedTextColor.LIGHT_PURPLE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!chairs().isChair(b)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (chairs().getRitual(b) != null) {
            say(p, "You cannot break a chair during a revival.", NamedTextColor.RED);
            return;
        }
        UUID owner = chairs().getOwner(b);
        if (owner != null && !owner.equals(p.getUniqueId()) && !p.hasPermission("malwaskin.admin")) {
            say(p, "This chair belongs to " + nameOf(owner) + ".", NamedTextColor.RED);
            return;
        }
        chairs().remove(b);
        b.setType(Material.AIR);
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.chair(1));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> chairs().isChair(b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> chairs().isChair(b));
    }

    // ---------------------------------------------------------------- interact

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Block clicked = e.getClickedBlock();

        if (clicked != null && chairs().isChair(clicked)) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
            handleChairClick(p, clicked, hand);
            return;
        }

        // Right-clicking a skin head anywhere else toggles equip.
        Items.SkinData data = Items.skinData(hand);
        if (data != null) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
            plugin.skins().toggleEquip(p, data);
        }
    }

    private void handleChairClick(Player p, Block chair, ItemStack hand) {
        UUID owner = chairs().getOwner(chair);

        if (chairs().getRitual(chair) != null) {
            say(p, "A revival is in progress here.", NamedTextColor.LIGHT_PURPLE);
            return;
        }

        // Holding a stolen skin: try to place it on the chair.
        if (Items.isSkin(hand)) {
            if (owner == null) {
                say(p, "Register this chair first: crouch and right-click it with an empty hand.", NamedTextColor.RED);
                return;
            }
            if (!owner.equals(p.getUniqueId())) {
                say(p, "This chair belongs to " + nameOf(owner) + ".", NamedTextColor.RED);
                return;
            }
            Items.SkinData data = Items.skinData(hand);
            if (data == null) return;
            if (plugin.getConfig().getBoolean("revival-chair.ritual.require-banned", true)
                    && !Bukkit.getOfflinePlayer(data.owner()).isBanned()) {
                say(p, data.name() + " is not banned — there is no one to revive.", NamedTextColor.RED);
                return;
            }
            if (chairs().ritualOf(p.getUniqueId()) != null) {
                say(p, "You are already performing a revival.", NamedTextColor.RED);
                return;
            }
            if (chairs().startRitual(p, chair, hand)) {
                hand.setAmount(hand.getAmount() - 1);
                p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
            }
            return;
        }

        // Otherwise: crouch + right-click registers the chair.
        if (p.isSneaking()) {
            if (owner == null) {
                chairs().setOwner(chair, p.getUniqueId());
                say(p, "This Revival Chair is now registered to you. No one else can use it.", NamedTextColor.GREEN);
            } else if (owner.equals(p.getUniqueId())) {
                say(p, "This chair is already yours.", NamedTextColor.YELLOW);
            } else {
                say(p, "This chair belongs to " + nameOf(owner) + ".", NamedTextColor.RED);
            }
        } else if (owner == null) {
            say(p, "Unregistered. Crouch and right-click to claim it.", NamedTextColor.GRAY);
        } else {
            say(p, "Registered to " + nameOf(owner) + ".", NamedTextColor.GRAY);
        }
    }

    // ------------------------------------------------------- raid mob control

    /** Raid mobs only ever target the reviver; endermen ignore everyone. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        Entity ent = e.getEntity();
        if (ent.getPersistentDataContainer().has(Keys.RITUAL_ENDER, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            return;
        }
        String reviver = ent.getPersistentDataContainer().get(Keys.RAID_MOB, PersistentDataType.STRING);
        if (reviver != null && (e.getTarget() == null || !e.getTarget().getUniqueId().toString().equals(reviver))) {
            Player p = Bukkit.getPlayer(UUID.fromString(reviver));
            if (p != null && p.isOnline() && !p.isDead()) {
                e.setTarget(p);
            } else {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRaidDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) damager = shooter;
        String reviver = damager.getPersistentDataContainer().get(Keys.RAID_MOB, PersistentDataType.STRING);
        if (reviver != null && e.getEntity() instanceof Player victim
                && !victim.getUniqueId().toString().equals(reviver)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderBlock(EntityChangeBlockEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(Keys.RITUAL_ENDER, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        RevivalRitual r = chairs().ritualOf(e.getPlayer().getUniqueId());
        if (r != null) r.fail("The reviver left the server.");
    }
}
