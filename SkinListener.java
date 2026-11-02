package com.malwa.skinstole;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class SkinListener implements Listener {
    private final MalwaSkinStole plugin;

    public SkinListener(MalwaSkinStole plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            plugin.skins().handleKill(killer, victim, e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.skins().reapplyOnJoin(e.getPlayer());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof SkinManager.SecondaryHolder
                && e.getPlayer() instanceof Player p) {
            plugin.skins().saveSecondary(p, e.getInventory());
        }
    }

    /** Skin heads are items, never placeable blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (Items.isSkin(e.getItemInHand())) e.setCancelled(true);
    }
}
