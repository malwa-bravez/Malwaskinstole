package com.malwa.skinstole;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/** Factory and checks for all custom items. */
public final class Items {
    public static final String TOKEN = "revive_token";
    public static final String STAR = "revive_star";
    public static final String CHAIR = "revival_chair";

    public record SkinData(UUID owner, String name, String value, String sig) {}

    private Items() {}

    private static Component name(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component lore(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack make(Material mat, int amount, String id, Component display, List<Component> lore) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(display);
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack token(int amount) {
        return make(Material.ECHO_SHARD, amount, TOKEN, name("Revive Token", NamedTextColor.AQUA),
                List.of(lore("Dropped by the Wizard Warden."), lore("Combine 9 to forge a Revive Star.")));
    }

    public static ItemStack star(int amount) {
        return make(Material.NETHER_STAR, amount, STAR, name("Revive Star", NamedTextColor.GOLD),
                List.of(lore("Forged from 9 Revive Tokens."), lore("3 are needed for a Revival Chair.")));
    }

    public static ItemStack chair(int amount) {
        return make(Material.LODESTONE, amount, CHAIR, name("Revival Chair", NamedTextColor.LIGHT_PURPLE),
                List.of(lore("Crouch + right-click to register it."),
                        lore("Place a stolen skin on it to begin a revival.")));
    }

    public static ItemStack skin(UUID owner, String ownerName, String value, String sig) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(owner, ownerName);
        if (value != null) {
            profile.setProperty(new ProfileProperty("textures", value, sig));
        }
        meta.setPlayerProfile(profile);
        meta.displayName(name(ownerName + "'s Stolen Skin", NamedTextColor.RED));
        meta.lore(List.of(lore("Right-click to equip / unequip."),
                lore("Place on a registered Revival Chair to revive " + ownerName + ".")));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.SKIN_OWNER, PersistentDataType.STRING, owner.toString());
        pdc.set(Keys.SKIN_NAME, PersistentDataType.STRING, ownerName);
        if (value != null) {
            pdc.set(Keys.SKIN_VALUE, PersistentDataType.STRING, value);
            pdc.set(Keys.SKIN_SIG, PersistentDataType.STRING, sig == null ? "" : sig);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static boolean is(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String v = item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID, PersistentDataType.STRING);
        return id.equals(v);
    }

    public static boolean isSkin(ItemStack item) {
        return skinData(item) != null;
    }

    /** Any plugin-made item; these are kept out of vanilla recipes. */
    public static boolean isCustom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(Keys.ITEM_ID, PersistentDataType.STRING) || pdc.has(Keys.SKIN_OWNER, PersistentDataType.STRING);
    }

    public static SkinData skinData(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String owner = pdc.get(Keys.SKIN_OWNER, PersistentDataType.STRING);
        if (owner == null) return null;
        try {
            String sig = pdc.get(Keys.SKIN_SIG, PersistentDataType.STRING);
            return new SkinData(UUID.fromString(owner),
                    pdc.getOrDefault(Keys.SKIN_NAME, PersistentDataType.STRING, "Unknown"),
                    pdc.get(Keys.SKIN_VALUE, PersistentDataType.STRING),
                    sig == null || sig.isEmpty() ? null : sig);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
