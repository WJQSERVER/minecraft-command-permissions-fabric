package com.github.tjeukayim.commandpermissionsfabric;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PermissionsMod implements ModInitializer {
    public static final String PREFIX = "minecraft.command.";
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if ("true".equals(System.getenv("minecraft-command-permissions.test"))) {
                var allCommands = dispatcher.getRoot().getChildren()
                        .stream()
                        .map(c -> "\"" + c.getName() + "\",")
                        .sorted()
                        .collect(Collectors.joining("\n"));
                LOGGER.info("All commands:\n{}", allCommands);
            }
            for (CommandNode<ServerCommandSource> node : dispatcher.getRoot().getChildren()) {
                alterCommand(node);
            }
            LOGGER.info("Loaded Minecraft Command Permissions");
        });
    }

    private void alterCommand(CommandNode<ServerCommandSource> child) {
        var name = child.getName();
        LOGGER.debug("Alter command {}", name);
        var packageName = commandPackageName(child);
        if (packageName == null || !packageName.startsWith("net.minecraft")) {
            LOGGER.debug("minecraft-command-permissions skipping command {} from {}", name, packageName);
            return;
        }
        try {
            var field = CommandNode.class.getDeclaredField("requirement");
            field.setAccessible(true);
            Predicate<ServerCommandSource> original = child.getRequirement();
            field.set(child, original.or((source) -> checkPermissionWithSelectorSupport(source, PREFIX + name)));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.warn("Failed to alter field CommandNode.requirement " + name, e);
        }
    }

    private boolean checkPermissionWithSelectorSupport(ServerCommandSource source, String permission) {
        try {
            List<ServerPlayerEntity> players = getPlayersFromSelector(source, permission);
            return players.stream().anyMatch(player -> Permissions.check(player.getCommandSource(), permission, false));
        } catch (IllegalArgumentException e) {
            return Permissions.check(source, permission, false);
        }
    }
    
    private String commandPackageName(CommandNode<ServerCommandSource> node) {
        var command = node.getCommand();
        if (command != null) {
            return command.getClass().getPackageName();
        }
        var redirect = node.getRedirect();
        if (redirect != null) {
            return commandPackageName(redirect);
        }
        for (var child : node.getChildren()) {
            var childResult = commandPackageName(child);
            if (childResult != null) {
                return childResult;
            }
        }
        return null;
    }

    public static List<ServerPlayerEntity> getPlayersFromSelector(ServerCommandSource source, String selectorString) {
        try {
            EntitySelectorReader reader = new EntitySelectorReader(new StringReader(selectorString));
            EntitySelector selector = reader.read();
            return selector.getPlayers(source);
        } catch (CommandSyntaxException e) {
            LOGGER.warn("解析选择器时发生语法错误: {}", selectorString, e);
            return List.of();
        } catch (IllegalArgumentException e) {
            LOGGER.warn("提供的选择器字符串格式不正确: {}", selectorString, e);
            return List.of();
        }
    }
}
