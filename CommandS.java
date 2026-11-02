package com.malwa.skinstole;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandS implements CommandExecutor, TabCompleter {
    private final MalwaSkinStole plugin;

    public CommandS(MalwaSkinStole plugin) {
        this.plugin = plugin;
    }

    private static void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    private static boolean admin(CommandSender s) {
        return s.hasPermission("malwaskin.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "ban" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 2) { msg(sender, "Usage: /s ban <player>", NamedTextColor.RED); return true; }
                if (!MalwaSkinStole.validName(args[1])) { msg(sender, "Invalid player name.", NamedTextColor.RED); return true; }
                plugin.banPlayer(args[1], "Banned by an administrator.");
                msg(sender, "Banned " + args[1] + ".", NamedTextColor.YELLOW);
            }
            case "unban" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 2) { msg(sender, "Usage: /s unban <player>", NamedTextColor.RED); return true; }
                if (!MalwaSkinStole.validName(args[1])) { msg(sender, "Invalid player name.", NamedTextColor.RED); return true; }
                plugin.unbanPlayer(args[1]);
                msg(sender, "Unbanned " + args[1] + ".", NamedTextColor.GREEN);
            }
            case "locate" -> locate(sender, args);
            case "spawn" -> spawn(sender, args);
            case "give" -> give(sender, args);
            case "inv" -> {
                if (!(sender instanceof Player p)) { msg(sender, "Players only.", NamedTextColor.RED); return true; }
                plugin.skins().openSecondary(p);
            }
            case "skin" -> skin(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private boolean requireAdmin(CommandSender s) {
        if (admin(s)) return true;
        msg(s, "You need to be an admin or owner for that.", NamedTextColor.RED);
        return false;
    }

    private void help(CommandSender s) {
        msg(s, "MalwaSkinStole commands:", NamedTextColor.GOLD);
        msg(s, "/s inv — open your secondary inventory", NamedTextColor.GRAY);
        msg(s, "/s skin <toggle|unequip> — equip the skin in your hand / restore yours", NamedTextColor.GRAY);
        if (admin(s)) {
            msg(s, "/s ban <player> · /s unban <player>", NamedTextColor.GRAY);
            msg(s, "/s spawn wizardwarden", NamedTextColor.GRAY);
            msg(s, "/s give <token|star|chair> [amount] [player]", NamedTextColor.GRAY);
        }
        msg(s, "/s locate revivechair — admins and creative players", NamedTextColor.GRAY);
    }

    private void locate(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("revivechair")) {
            msg(sender, "Usage: /s locate revivechair", NamedTextColor.RED);
            return;
        }
        boolean creative = sender instanceof Player p && p.getGameMode() == GameMode.CREATIVE;
        if (!admin(sender) && !creative) {
            msg(sender, "Only admins, owners and creative players can locate chairs.", NamedTextColor.RED);
            return;
        }
        Location found = sender instanceof Player p ? plugin.chairs().nearest(p.getLocation()) : plugin.chairs().any();
        if (found == null) {
            msg(sender, "No Revival Chair found" + (sender instanceof Player ? " in this world." : "."), NamedTextColor.YELLOW);
            return;
        }
        String dist = sender instanceof Player p
                ? " (" + (int) p.getLocation().distance(found) + " blocks away)" : "";
        msg(sender, "Nearest Revival Chair: " + found.getBlockX() + ", " + found.getBlockY() + ", "
                + found.getBlockZ() + " in " + found.getWorld().getName() + dist, NamedTextColor.LIGHT_PURPLE);
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2 || !args[1].equalsIgnoreCase("wizardwarden")) {
            msg(sender, "Usage: /s spawn wizardwarden", NamedTextColor.RED);
            return;
        }
        if (!(sender instanceof Player p)) {
            msg(sender, "Run this as a player (it searches for an Ancient City near you).", NamedTextColor.RED);
            return;
        }
        msg(sender, "Searching for the nearest Ancient City...", NamedTextColor.GRAY);
        Location at = plugin.wardens().spawnInAncientCity(p);
        if (at != null) {
            msg(sender, "The Wizard Warden stirs in a hidden room at " + at.getBlockX() + ", "
                    + at.getBlockY() + ", " + at.getBlockZ() + ".", NamedTextColor.DARK_PURPLE);
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            msg(sender, "Usage: /s give <token|star|chair> [amount] [player]", NamedTextColor.RED);
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ex) {
                msg(sender, "Amount must be a number.", NamedTextColor.RED);
                return;
            }
        }
        Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
        if (target == null) {
            msg(sender, "Target player not found.", NamedTextColor.RED);
            return;
        }
        ItemStack item = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "token" -> Items.token(amount);
            case "star" -> Items.star(amount);
            case "chair" -> Items.chair(amount);
            default -> null;
        };
        if (item == null) {
            msg(sender, "Unknown item. Use token, star or chair.", NamedTextColor.RED);
            return;
        }
        target.getInventory().addItem(item).values()
                .forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
        msg(sender, "Gave " + amount + " " + args[1] + " to " + target.getName() + ".", NamedTextColor.GREEN);
    }

    private void skin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Players only.", NamedTextColor.RED);
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        switch (sub) {
            case "toggle", "equip" -> {
                if (plugin.skins().isEquipped(p)) {
                    plugin.skins().unequip(p);
                    return;
                }
                Items.SkinData data = Items.skinData(p.getInventory().getItemInMainHand());
                if (data == null) msg(p, "Hold a stolen skin in your main hand.", NamedTextColor.RED);
                else plugin.skins().equip(p, data);
            }
            case "unequip", "reset" -> {
                if (plugin.skins().isEquipped(p)) plugin.skins().unequip(p);
                else msg(p, "You are not wearing a stolen skin.", NamedTextColor.YELLOW);
            }
            default -> msg(p, "Usage: /s skin <toggle|unequip>", NamedTextColor.RED);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("inv", "skin", "locate"));
            if (admin(sender)) out.addAll(List.of("ban", "unban", "spawn", "give"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "locate" -> out.add("revivechair");
                case "spawn" -> { if (admin(sender)) out.add("wizardwarden"); }
                case "give" -> { if (admin(sender)) out.addAll(List.of("token", "star", "chair")); }
                case "skin" -> out.addAll(List.of("toggle", "unequip"));
                case "ban", "unban" -> { if (admin(sender)) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName())); }
                default -> { }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && admin(sender)) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
