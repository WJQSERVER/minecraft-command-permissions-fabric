import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
            Predicate<ServerCommandSource> newRequirement = source -> {
                if (Permissions.check(source, PREFIX + name, false)) {
                    return true;
                }
                // 新增选择器支持的检查逻辑
                CommandContext<ServerCommandSource> context = source.getCommandContext();
                if (context != null && context.hasNodes()) {
                    // 检查是否包含选择器类型，并验证权限
                    // 这里是示例逻辑，具体实现可能需要根据选择器的实现方式调整
                    return context.getNodes().stream()
                        .anyMatch(node -> node.getNode().getName().equals("someSelector") &&
                                           Permissions.check(source, "minecraft.command.selector." + node.getNode().getName(), false));
                }
                return false;
            };
            field.set(child, original.or(newRequirement));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.warn("Failed to alter field CommandNode.requirement " + name, e);
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
}
