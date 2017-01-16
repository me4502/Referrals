package com.me4502.referrals.sponge;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.me4502.referrals.DatabaseManager;
import com.me4502.referrals.Referrals;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Plugin(
        id = "referrals",
        name = "Referrals",
        description = "Referrals plugin for Sponge",
        version = "1.0",
        authors = {
                "Me4502"
        }
)
public class ReferralsSponge implements Referrals {

    @Inject
    @DefaultConfig(sharedRoot = true)
    private Path defaultConfig;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    private DatabaseManager databaseManager;

    private List<String> playerRewards = new ArrayList<>();
    private List<String> sourceRewards = new ArrayList<>();

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        loadConfig();

        PaginationService paginationService = Sponge.getServiceManager().provide(PaginationService.class).get();

        UserStorageService userStorageService = Sponge.getServiceManager().provide(UserStorageService.class).get();

        CommandSpec playerReferralCommand = CommandSpec.builder()
                .arguments(GenericArguments.user(Text.of("source")))
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of("Only players can use this command!"));
                        return CommandResult.empty();
                    }

                    if (getDatabaseManager().hasAlreadyReferred(((Player) src).getUniqueId())) {
                        src.sendMessage(Text.of("You have already been referred!"));
                        return CommandResult.empty();
                    }

                    User source = args.<User>getOne("source").orElse(null);
                    if (source == null) {
                        src.sendMessage(Text.of("A player must be provided!"));
                        return CommandResult.empty();
                    }

                    boolean rewarded = false;

                    for (String command : playerRewards) {
                        Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command.replace("@p", src.getName()));
                    }

                    if (source.isOnline()) {
                        for (String command : sourceRewards) {
                            Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command.replace("@p", source.getName()));
                        }
                        source.getPlayer().get().sendMessage(Text.of("You've been rewarded for referring " + src.getName()));
                        rewarded = true;
                    }

                    getDatabaseManager().addPlayerReferral(((Player) src).getUniqueId(), source.getUniqueId(), rewarded);
                    return CommandResult.success();
                })
                .build();

        CommandSpec genericReferralCommand = CommandSpec.builder()
                .arguments(GenericArguments.string(Text.of("source")))
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(Text.of("Only players can use this command!"));
                        return CommandResult.empty();
                    }

                    if (getDatabaseManager().hasAlreadyReferred(((Player) src).getUniqueId())) {
                        src.sendMessage(Text.of("You have already been referred!"));
                        return CommandResult.empty();
                    }

                    for (String command : playerRewards) {
                        Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command.replace("@p", src.getName()));
                    }

                    getDatabaseManager().addWebsiteReferral(((Player) src).getUniqueId(), args.<String>getOne("source").orElse(null));
                    return CommandResult.success();
                })
                .build();

        CommandSpec listReferralCommand = CommandSpec.builder()
                .arguments(
                        GenericArguments.optional(GenericArguments.user(Text.of("user")))
                )
                .executor((src, args) -> {
                    User lookupUser = null;
                    if (src instanceof Player) {
                        lookupUser = ((Player) src);
                    }
                    if (args.hasAny("user") && src.hasPermission("referrals.list.other")) {
                        lookupUser = args.<User>getOne("user").get();
                    }
                    if (lookupUser == null) {
                        src.sendMessage(Text.of("A player must be provided!"));
                        return CommandResult.empty();
                    }

                    PaginationList.Builder pagination = paginationService.builder();
                    pagination.title(Text.of(TextColors.YELLOW, "Referrals"));
                    pagination.padding(Text.of("="));
                    pagination.contents(getDatabaseManager().getPlayersReferred(lookupUser.getUniqueId()).entrySet().stream().map(entry -> Text.of(userStorageService.get(entry.getKey()) + " was referred at " + DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").format(entry.getValue().toInstant()))).collect(Collectors.toList()));
                    pagination.sendTo(src);

                    return CommandResult.success();
                })
                .build();

        CommandSpec deleteCommand = CommandSpec.builder()
                .arguments(
                        GenericArguments.optional(GenericArguments.user(Text.of("user")))
                )
                .executor((src, args) -> {
                    User lookupUser = null;
                    if (args.hasAny("user")) {
                        lookupUser = args.<User>getOne("user").get();
                    }
                    if (lookupUser == null) {
                        getDatabaseManager().clearAll();
                    } else {
                        getDatabaseManager().clearAll(lookupUser.getUniqueId());
                    }

                    return CommandResult.success();
                })
                .permission("referral.delete")
                .build();

        CommandSpec referralCommand = CommandSpec.builder()
                .child(playerReferralCommand, "player")
                .child(genericReferralCommand, "website")
                .child(listReferralCommand, "list")
                .child(deleteCommand, "delete")
                .build();

        Sponge.getCommandManager().register(this, referralCommand, "referral", "refer");
    }

    @Listener
    public void onServerReload(GameReloadEvent event) {
        databaseManager.disconnect();
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (!Files.exists(defaultConfig, LinkOption.NOFOLLOW_LINKS)) {
                URL jarConfigFile = this.getClass().getResource("default.conf");
                ConfigurationLoader<CommentedConfigurationNode> loader = HoconConfigurationLoader.builder().setURL(jarConfigFile).build();
                configManager.save(loader.load());
            }

            ConfigurationNode node = configManager.load();
            node.getOptions().setShouldCopyDefaults(true);

            String user = node.getNode("jdbc-username").getString("root");
            String password = node.getNode("jdbc-password").getString("password");
            String jdbcUrl = node.getNode("jdbc-url").getString("jdbc:mysql://localhost:3306/minecraft");

            playerRewards = node.getNode("player-rewards").getList(TypeToken.of(String.class), Lists.newArrayList("give @p 1"));
            sourceRewards = node.getNode("source-rewards").getList(TypeToken.of(String.class), Lists.newArrayList("give @p 1"));

            databaseManager = new DatabaseManager(jdbcUrl, user, password);
            databaseManager.connect();

            configManager.save(node);
        } catch (IOException | ObjectMappingException e) {
            e.printStackTrace();
        }
    }

    @Listener
    public void onServerStopping(GameStoppingServerEvent event) {
        databaseManager.disconnect();
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        for (UUID pendingReward : getDatabaseManager().getPendingRewards(event.getTargetEntity().getUniqueId())) {
            UserStorageService userStorageService = Sponge.getServiceManager().provide(UserStorageService.class).get();
            User user = userStorageService.get(pendingReward).get();

            for (String command : sourceRewards) {
                Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command.replace("@p", event.getTargetEntity().getName()));
            }
            event.getTargetEntity().getPlayer().get().sendMessage(Text.of("You've been rewarded for referring " + user.getName()));
        }
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }
}
