package com.malwa.skinstole;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Pattern;

public final class MalwaSkinStole extends JavaPlugin {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_.]{1,16}");

    private SkinManager skins;
    private ChairManager chairs;
    private WizardWardenManager wardens;
    private NamespacedKey starRecipeKey;
    private NamespacedKey chairRecipeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);
        skins = new SkinManager(this);
        chairs = new ChairManager(this);
        wardens = new WizardWardenManager(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new SkinListener(this), this);
        pm.registerEvents(new ChairListener(this), this);
        pm.registerEvents(new CraftListener(this), this);
        pm.registerEvents(wardens, this);

        registerRecipes();

        PluginCommand cmd = getCommand("s");
        if (cmd != null) {
            CommandS handler = new CommandS(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("MalwaSkinStole enabled.");
    }

    @Override
    public void onDisable() {
        if (chairs != null) chairs.shutdown();
        if (skins != null) skins.saveOpenInventories();
        if (wardens != null) wardens.shutdown();
    }

    private void registerRecipes() {
        starRecipeKey = new NamespacedKey(this, "revive_star");
        chairRecipeKey = new NamespacedKey(this, "revival_chair");
        Bukkit.removeRecipe(starRecipeKey);
        Bukkit.removeRecipe(chairRecipeKey);

        // 9 Revive Tokens -> 1 Revive Star
        ShapelessRecipe star = new ShapelessRecipe(starRecipeKey, Items.star(1));
        for (int i = 0; i < 9; i++) {
            star.addIngredient(new RecipeChoice.ExactChoice(Items.token(1)));
        }
        Bukkit.addRecipe(star);

        // Gold Block | Star | Gold Block
        // Star       | Beacon | Star
        // Gold Block | Diamond Block | Gold Block
        ShapedRecipe chair = new ShapedRecipe(chairRecipeKey, Items.chair(1));
        chair.shape("GSG", "SBS", "GDG");
        chair.setIngredient('G', Material.GOLD_BLOCK);
        chair.setIngredient('S', new RecipeChoice.ExactChoice(Items.star(1)));
        chair.setIngredient('B', Material.BEACON);
        chair.setIngredient('D', Material.DIAMOND_BLOCK);
        Bukkit.addRecipe(chair);
    }

    public static boolean validName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /** Bans through the server's own ban system (kicks online players). */
    public boolean banPlayer(String name, String reason) {
        if (!validName(name)) return false;
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + name + " " + reason);
    }

    public boolean unbanPlayer(String name) {
        if (!validName(name)) return false;
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + name);
    }

    public SkinManager skins() { return skins; }
    public ChairManager chairs() { return chairs; }
    public WizardWardenManager wardens() { return wardens; }
    public NamespacedKey starRecipeKey() { return starRecipeKey; }
    public NamespacedKey chairRecipeKey() { return chairRecipeKey; }
}
