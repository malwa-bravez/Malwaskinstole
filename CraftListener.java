package com.malwa.skinstole;

import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class CraftListener implements Listener {
    private final MalwaSkinStole plugin;

    public CraftListener(MalwaSkinStole plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (recipe == null) return;

        if (recipe instanceof Keyed keyed) {
            if (keyed.getKey().equals(plugin.chairRecipeKey())) {
                int max = plugin.getConfig().getInt("revival-chair.max-in-world", 7);
                if (plugin.chairs().count() >= max) e.getInventory().setResult(null);
                return;
            }
            if (keyed.getKey().equals(plugin.starRecipeKey())) return;
        }

        // Custom items may not be used as ingredients in any other recipe.
        for (ItemStack stack : e.getInventory().getMatrix()) {
            if (Items.isCustom(stack)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
