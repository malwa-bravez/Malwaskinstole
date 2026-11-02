package com.malwa.skinstole;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** Kill counting, skin theft, skin equipping, permadeath and the secondary inventory. */
public final class SkinManager {
    private static final int SECONDARY_SIZE = 27;

    private final MalwaSkinStole plugin;

    public SkinManager(MalwaSkinStole plugin) {
        this.plugin = plugin;
    }

    /** Marker holder so we can recognise our secondary inventory on close. */
    public static final class SecondaryHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    // ------------------------------------------------------------------ kills

    public void handleKill(Player killer, Player victim, PlayerDeathEvent event) {
        int needed = plugin.getConfig().getInt("skin.kills-to-steal", 10);
        PersistentDataContainer kp = killer.getPersistentDataContainer();
        var key = Keys.kills(victim.getUniqueId());
        int kills = kp.getOrDefault(key, PersistentDataType.INTEGER, 0) + 1;
        kp.set(key, PersistentDataType.INTEGER, kills);

        if (kills == needed) {
            stealSkin(killer, victim);
        } else if (kills < needed) {
            killer.sendActionBar(Component.text("Kills on " + victim.getName() + ": " + kills + "/" + needed,
                    NamedTextColor.GRAY));
        }

        // Permadeath only applies once this player's skin has been stolen.
        PersistentDataContainer vp = victim.getPersistentDataContainer();
        if (vp.has(Keys.SKIN_STOLEN, PersistentDataType.BYTE)) {
            int limit = plugin.getConfig().getInt("skin.deaths-to-permaban", 7);
            int deaths = vp.getOrDefault(Keys.STOLEN_DEATHS, PersistentDataType.INTEGER, 0) + 1;
            vp.set(Keys.STOLEN_DEATHS, PersistentDataType.INTEGER, deaths);
            if (deaths >= limit) {
                permaban(victim, event);
            } else {
                victim.sendMessage(Component.text("Your skin was stolen. Deaths to players: " + deaths + "/" + limit
                        + ". At " + limit + " you are permanently banned.", NamedTextColor.DARK_RED));
            }
        }
    }

    private void stealSkin(Player killer, Player victim) {
        String[] tex = ownTextures(victim);
        ItemStack skin = Items.skin(victim.getUniqueId(), victim.getName(),
                tex == null ? null : tex[0], tex == null ? null : tex[1]);
        victim.getPersistentDataContainer().set(Keys.SKIN_STOLEN, PersistentDataType.BYTE, (byte) 1);

        var leftovers = killer.getInventory().addItem(skin);
        leftovers.values().forEach(i -> killer.getWorld().dropItemNaturally(killer.getLocation(), i));

        killer.sendMessage(Component.text("You stole " + victim.getName() + "'s skin!", NamedTextColor.GOLD));
        victim.sendMessage(Component.text(killer.getName() + " stole your skin!", NamedTextColor.DARK_RED));
        Bukkit.broadcast(Component.text(killer.getName() + " has stolen " + victim.getName() + "'s skin!",
                NamedTextColor.RED));
    }

    private void permaban(Player victim, PlayerDeathEvent event) {
        String[] tex = ownTextures(victim);
        event.getDrops().add(Items.skin(victim.getUniqueId(), victim.getName(),
                tex == null ? null : tex[0], tex == null ? null : tex[1]));
        String name = victim.getName();
        Bukkit.broadcast(Component.text(name + " lost their skin and has been permanently banned.",
                NamedTextColor.DARK_RED));
        // Run after the death is fully processed so the drops land.
        Bukkit.getScheduler().runTask(plugin, () -> plugin.banPlayer(name, "Permadeath: your skin was stolen and you died too many times."));
    }

    // ------------------------------------------------------------- textures

    private String[] currentTextures(Player p) {
        for (ProfileProperty prop : p.getPlayerProfile().getProperties()) {
            if (prop.getName().equals("textures")) {
                return new String[]{prop.getValue(), prop.getSignature()};
            }
        }
        return null;
    }

    /** The player's real skin, even while a stolen skin is equipped. */
    private String[] ownTextures(Player p) {
        String orig = p.getPersistentDataContainer().get(Keys.ORIG_TEX, PersistentDataType.STRING);
        if (orig != null) return decode(orig);
        return currentTextures(p);
    }

    private static String encode(String[] tex) {
        return tex[0] + "\n" + (tex[1] == null ? "" : tex[1]);
    }

    private static String[] decode(String s) {
        String[] parts = s.split("\n", -1);
        return new String[]{parts[0], parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null};
    }

    // --------------------------------------------------------------- equip

    public boolean isEquipped(Player p) {
        return p.getPersistentDataContainer().has(Keys.EQUIPPED, PersistentDataType.STRING);
    }

    public void toggleEquip(Player p, Items.SkinData skin) {
        if (isEquipped(p)) {
            unequip(p);
        } else {
            equip(p, skin);
        }
    }

    public void equip(Player p, Items.SkinData skin) {
        if (skin.value() == null) {
            p.sendMessage(Component.text("This skin has no texture data.", NamedTextColor.RED));
            return;
        }
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(Keys.ORIG_TEX, PersistentDataType.STRING)) {
            String[] cur = currentTextures(p);
            if (cur != null) pdc.set(Keys.ORIG_TEX, PersistentDataType.STRING, encode(cur));
        }
        pdc.set(Keys.EQUIPPED, PersistentDataType.STRING, skin.name() + "\n" + encode(new String[]{skin.value(), skin.sig()}));
        apply(p, skin.value(), skin.sig());
        p.sendMessage(Component.text("Equipped " + skin.name() + "'s skin.", NamedTextColor.GREEN));
    }

    public void unequip(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String orig = pdc.get(Keys.ORIG_TEX, PersistentDataType.STRING);
        pdc.remove(Keys.EQUIPPED);
        if (orig != null) {
            String[] tex = decode(orig);
            apply(p, tex[0], tex[1]);
            pdc.remove(Keys.ORIG_TEX);
        }
        p.sendMessage(Component.text("Skin unequipped.", NamedTextColor.YELLOW));
    }

    /** Re-applies an equipped skin after login (the profile resets on join). */
    public void reapplyOnJoin(Player p) {
        String eq = p.getPersistentDataContainer().get(Keys.EQUIPPED, PersistentDataType.STRING);
        if (eq == null) return;
        int nl = eq.indexOf('\n');
        if (nl < 0) return;
        String[] tex = decode(eq.substring(nl + 1));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) apply(p, tex[0], tex[1]);
        }, 10L);
    }

    private void apply(Player p, String value, String sig) {
        PlayerProfile profile = p.getPlayerProfile();
        profile.setProperty(new ProfileProperty("textures", value, sig));
        p.setPlayerProfile(profile);
        refreshFor(p);
    }

    private void refreshFor(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p)) other.hidePlayer(plugin, p);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(p)) other.showPlayer(plugin, p);
            }
        }, 2L);
    }

    // ------------------------------------------------------ secondary inventory

    public void openSecondary(Player p) {
        SecondaryHolder holder = new SecondaryHolder();
        Inventory inv = Bukkit.createInventory(holder, SECONDARY_SIZE, Component.text("Secondary Inventory"));
        holder.inventory = inv;
        String data = p.getPersistentDataContainer().get(Keys.SECONDARY, PersistentDataType.STRING);
        if (data != null) {
            ItemStack[] items = decodeItems(data);
            if (items != null) {
                for (int i = 0; i < Math.min(items.length, SECONDARY_SIZE); i++) inv.setItem(i, items[i]);
            }
        }
        p.openInventory(inv);
    }

    public void saveSecondary(Player p, Inventory inv) {
        String data = encodeItems(inv.getContents());
        if (data != null) p.getPersistentDataContainer().set(Keys.SECONDARY, PersistentDataType.STRING, data);
    }

    public void saveOpenInventories() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof SecondaryHolder) saveSecondary(p, top);
        }
    }

    private static String encodeItems(ItemStack[] items) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bo)) {
            out.writeInt(items.length);
            for (ItemStack item : items) out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(bo.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    private static ItemStack[] decodeItems(String data) {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            ItemStack[] items = new ItemStack[in.readInt()];
            for (int i = 0; i < items.length; i++) items[i] = (ItemStack) in.readObject();
            return items;
        } catch (Exception ex) {
            return null;
        }
    }
}
