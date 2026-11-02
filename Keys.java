package com.malwa.skinstole;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Central place for every PersistentDataContainer key used by the plugin. */
public final class Keys {
    private static Plugin plugin;

    public static NamespacedKey ITEM_ID, SKIN_OWNER, SKIN_NAME, SKIN_VALUE, SKIN_SIG;
    public static NamespacedKey WIZARD, WIZARD_MINION, WIZARD_LASER, WIZARD_MILESTONES;
    public static NamespacedKey RAID_MOB, RITUAL_ENDER;
    public static NamespacedKey SKIN_STOLEN, STOLEN_DEATHS, EQUIPPED, ORIG_TEX, SECONDARY;

    private Keys() {}

    public static void init(Plugin p) {
        plugin = p;
        ITEM_ID = key("item_id");
        SKIN_OWNER = key("skin_owner");
        SKIN_NAME = key("skin_name");
        SKIN_VALUE = key("skin_value");
        SKIN_SIG = key("skin_sig");
        WIZARD = key("wizard_warden");
        WIZARD_MINION = key("wizard_minion");
        WIZARD_LASER = key("wizard_laser_fired");
        WIZARD_MILESTONES = key("wizard_milestones");
        RAID_MOB = key("raid_mob");
        RITUAL_ENDER = key("ritual_ender");
        SKIN_STOLEN = key("skin_stolen");
        STOLEN_DEATHS = key("stolen_deaths");
        EQUIPPED = key("equipped_skin");
        ORIG_TEX = key("original_textures");
        SECONDARY = key("secondary_inventory");
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    /** Per-victim kill counter stored on the killer. */
    public static NamespacedKey kills(UUID victim) {
        return new NamespacedKey(plugin, "kills_" + victim);
    }

    /** Per-chair data stored in the chunk container. */
    public static NamespacedKey chair(int x, int y, int z) {
        return new NamespacedKey(plugin, "chair_" + x + "_" + y + "_" + z);
    }
}
