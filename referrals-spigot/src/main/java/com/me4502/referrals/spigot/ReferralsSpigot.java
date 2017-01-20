package com.me4502.referrals.spigot;

import com.me4502.referrals.DatabaseManager;
import com.me4502.referrals.Referrals;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ReferralsSpigot extends JavaPlugin implements Referrals, Listener {

    private DatabaseManager databaseManager;

    private List<String> playerRewards = new ArrayList<>();
    private List<String> sourceRewards = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(new File(getDataFolder(), "config.yml"));
            config.options().copyDefaults(true);

            String user = config.getString("jdbc-username", "root");
            String password = config.getString("jdbc-password", "password");
            String jdbcUrl = config.getString("jdbc-url", "jdbc:mysql://localhost:3306/minecraft");

            playerRewards = config.getStringList("player-rewards");
            sourceRewards = config.getStringList("source-rewards");

            databaseManager = new DatabaseManager(jdbcUrl, user, password);
            databaseManager.connect();

            config.save(new File(getDataFolder(), "config.yml"));
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equals("referral")) {
            if (args.length >= 1) {
                switch (args[0]) {
                    case "reload": {
                        databaseManager.disconnect();
                        loadConfig();
                        sender.sendMessage(ChatColor.GREEN + "Refer configs have been reloaded.");
                        return true;
                    }
                    case "list": {
                        OfflinePlayer lookupUser = null;
                        if (sender instanceof Player) {
                            lookupUser = (Player) sender;
                        }
                        if (args.length == 2 && sender.hasPermission("referrals.list.other")) {
                            lookupUser = Bukkit.getOfflinePlayer(args[1]);
                        }
                        if (lookupUser == null) {
                            sender.sendMessage("A player must be provided!");
                            return true;
                        }

                        sender.sendMessage(ChatColor.YELLOW + "Referrals");

                        for (String message : getDatabaseManager().getPlayersReferred(lookupUser.getUniqueId()).entrySet().stream().map(entry -> Bukkit.getOfflinePlayer(entry.getKey()).getName() + " was referred at " + DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a").format(entry.getValue().toLocalDateTime())).collect(Collectors.toList())) {
                            sender.sendMessage(message);
                        }

                        return true;
                    }
                    case "player": {
                        if (!(sender instanceof Player)) {
                            sender.sendMessage("Only players can use this command!");
                            return true;
                        }

                        if (getDatabaseManager().hasAlreadyReferred(((Player) sender).getUniqueId())) {
                            sender.sendMessage(ChatColor.RED + "You have already selected who referred you, you can't do it again!");
                            return true;
                        }

                        OfflinePlayer source = null;
                        if (args.length == 2) {
                            source = Bukkit.getOfflinePlayer(args[1]);
                        }

                        if (source == null || !source.hasPlayedBefore()) {
                            sender.sendMessage(ChatColor.RED + args[1] + " not found. Try again.");
                            return true;
                        }

                        if (source.getUniqueId().equals(((Player) sender).getUniqueId())) {
                            sender.sendMessage(ChatColor.RED + "You can't refer yourself, you silly goose!");
                            return true;
                        }

                        boolean rewarded = false;

                        for (String command : playerRewards) {
                            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command.replace("@p", sender.getName()));
                        }

                        if (source.isOnline()) {
                            for (String command : sourceRewards) {
                                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command.replace("@p", source.getName()));
                            }
                            source.getPlayer().sendMessage(ChatColor.GREEN + "Thanks for referring " + sender.getName() + " to the server!");
                            rewarded = true;
                        }

                        sender.sendMessage(ChatColor.GREEN + "You were referred by " + source.getName() + ". Want some awesome rewards? Get a "
                                + "player to join and have them do " + ChatColor.YELLOW + "/refer player <playername>");
                        getDatabaseManager().addPlayerReferral(((Player) sender).getUniqueId(), source.getUniqueId(), rewarded);
                        return true;
                    }
                    case "website": {
                        if (!(sender instanceof Player)) {
                            sender.sendMessage("Only players can use this command!");
                            return true;
                        }

                        if (getDatabaseManager().hasAlreadyReferred(((Player) sender).getUniqueId())) {
                            sender.sendMessage(ChatColor.RED + "You have already selected who referred you, you can't do it again!");
                            return true;
                        }

                        String source = null;
                        if (args.length == 2) {
                            source = args[1];
                        }

                        if (source == null) {
                            sender.sendMessage(ChatColor.RED + "Please type /refer website <websitename>");
                            return true;
                        }

                        for (String command : playerRewards) {
                            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command.replace("@p", sender.getName()));
                        }

                        sender.sendMessage(ChatColor.GREEN + "You were referred by " + source + ". Want some awesome rewards? Get a player to join "
                                + "and have them do " + ChatColor.YELLOW + "/refer player <playername>");
                        getDatabaseManager().addWebsiteReferral(((Player) sender).getUniqueId(), source);
                        return true;
                    }
                    case "delete": {
                        OfflinePlayer lookupUser = null;
                        if (args.length == 2) {
                            lookupUser = Bukkit.getOfflinePlayer(args[1]);
                        }
                        if (lookupUser == null || !lookupUser.hasPlayedBefore()) {
                            sender.sendMessage(ChatColor.GREEN + "Deleted all referral information.");
                            getDatabaseManager().clearAll();
                        } else {
                            sender.sendMessage(ChatColor.GREEN + "Deleted referral information for player " + lookupUser.getName() + ".");
                            getDatabaseManager().clearAll(lookupUser.getUniqueId());
                        }

                        return true;
                    }
                    default: {
                        sender.sendMessage(ChatColor.GREEN + "===" + ChatColor.YELLOW + "Refer" + ChatColor.GREEN + "===");
                        sender.sendMessage(ChatColor.GREEN + "Help the server (and maybe others) out, and get rewarded yourself!");
                        sender.sendMessage(ChatColor.GREEN + "How’d you get here? By player, by server list, or by website and state which.");
                        sender.sendMessage(ChatColor.GREEN + "/refer player/website/list <playername/website/list referrals>");
                        sender.sendMessage(ChatColor.GREEN + "If you haven't already, you can have others refer you for bringing them here.");
                        return true;
                    }
                }
            } else {
                sender.sendMessage(ChatColor.GREEN + "===" + ChatColor.YELLOW + "Refer" + ChatColor.GREEN + "===");
                sender.sendMessage(ChatColor.GREEN + "Help the server (and maybe others) out, and get rewarded yourself!");
                sender.sendMessage(ChatColor.GREEN + "How’d you get here? By player, by server list, or by website and state which.");
                sender.sendMessage(ChatColor.GREEN + "/refer player/website/list <playername/website/list referrals>");
                sender.sendMessage(ChatColor.GREEN + "If you haven't already, you can have others refer you for bringing them here.");
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        for (UUID pendingReward : getDatabaseManager().getPendingRewards(event.getPlayer().getUniqueId())) {
            OfflinePlayer user = Bukkit.getOfflinePlayer(pendingReward);

            for (String command : sourceRewards) {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command.replace("@p", event.getPlayer().getName()));
            }
            event.getPlayer().sendMessage(ChatColor.GREEN + "Thanks for referring " + user.getName() + " to the server!");
        }
    }

    @Override
    public void onDisable() {
        databaseManager.disconnect();
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }
}
