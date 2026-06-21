package dev.dromzeh.immortail.command;

import static dev.dromzeh.immortail.command.CommandHelper.*;

import dev.dromzeh.immortail.Immortail;
import dev.dromzeh.immortail.protection.ProtectionManager;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class ImmortailCommand implements CommandExecutor, TabCompleter {

  private final Immortail plugin;
  private final ListHandler listHandler;
  private final InfoHandler infoHandler;
  private final AggroHandler aggroHandler;
  private final PetsHandler petsHandler;

  public ImmortailCommand(Immortail plugin) {
    this.plugin = plugin;
    this.listHandler =
        new ListHandler(plugin.getProtection(), plugin.getRegistry(), plugin.getPermissions());
    this.infoHandler = new InfoHandler(plugin, plugin.getProtection(), plugin.getRegistry());
    this.aggroHandler = new AggroHandler(plugin, plugin.getProtection(), plugin.getPermissions());
    this.petsHandler = new PetsHandler(plugin.getRegistry());
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      sendHelp(sender);
      return true;
    }

    switch (args[0].toLowerCase()) {
      case "list" -> listHandler.execute(sender, args);
      case "info" -> infoHandler.execute(sender);
      case "mode" -> handleMode(sender, args);
      case "aggro" -> aggroHandler.execute(sender, args);
      case "pets" -> petsHandler.execute(sender, args);
      case "defuse" -> handleDefuse(sender);
      case "prune" -> handlePrune(sender);
      case "reload" -> handleReload(sender);
      default ->
          sender.sendMessage(
              Component.text("unknown subcommand. try /immortail", NamedTextColor.RED));
    }

    return true;
  }

  private void sendHelp(CommandSender sender) {
    header(sender, "tamed mobs never die");
    help(sender, "list", "show all protected mobs");
    help(sender, "list <player>", "show a player's protected mobs");
    help(sender, "info", "plugin status and settings");
    help(sender, "mode [invulnerable|resistance]", "view or change protection mode");
    help(sender, "aggro [setting|player]", "view or change aggression settings");
    help(sender, "pets <player>", "list a player's mobs with details");
    help(sender, "defuse", "calm all angry protected mobs");
    help(sender, "prune", "drop stale tracked-mob records (dead worlds, deleted mobs)");
    help(sender, "reload", "re-read config and re-sync");
    sender.sendMessage(Component.text(""));
  }

  private void handleMode(CommandSender sender, String[] args) {
    if (!requireAdmin(sender)) return;
    if (args.length == 1) {
      kv(sender, "current mode: ", plugin.getMode());
      return;
    }
    String newMode = args[1].toLowerCase();
    if (!newMode.equals("invulnerable") && !newMode.equals("resistance")) {
      sender.sendMessage(
          Component.text("invalid mode. use: invulnerable or resistance", NamedTextColor.RED));
      return;
    }
    plugin.setMode(newMode);
    plugin.getProtection().syncAll();
    sender.sendMessage(
        Component.text("protection mode set to: ", NamedTextColor.GREEN)
            .append(Component.text(newMode, NamedTextColor.WHITE)));
  }

  private void handleDefuse(CommandSender sender) {
    if (!requireAdmin(sender)) return;
    plugin.getProtection().defuseAll();
    sender.sendMessage(Component.text("all protected mobs defused", NamedTextColor.GREEN));
  }

  private void handlePrune(CommandSender sender) {
    if (!requireAdmin(sender)) return;
    if (plugin.getProtection().isPruning()) {
      sender.sendMessage(Component.text("a prune is already running", NamedTextColor.RED));
      return;
    }
    sender.sendMessage(label("pruning tracked mobs..."));
    plugin.getProtection().prune().thenAccept(result -> sendPruneResult(sender, result));
  }

  private void sendPruneResult(CommandSender sender, ProtectionManager.PruneResult result) {
    Component msg =
        Component.text("pruned ", NamedTextColor.GREEN)
            .append(Component.text(result.removed() + " stale record(s)", NamedTextColor.WHITE));
    if (result.checked() > 0) {
      msg =
          msg.append(Component.text(" (checked ", NamedTextColor.GRAY))
              .append(Component.text(String.valueOf(result.checked()), NamedTextColor.WHITE))
              .append(Component.text(" unloaded, ", NamedTextColor.GRAY))
              .append(Component.text(result.offlineDeleted() + " missing", NamedTextColor.WHITE))
              .append(Component.text(")", NamedTextColor.GRAY));
    }
    sender.sendMessage(msg);
  }

  private void handleReload(CommandSender sender) {
    if (!requireAdmin(sender)) return;
    plugin.reloadSettings();
    plugin.getProtection().syncAll();
    var p = plugin.getProtection();
    sender.sendMessage(
        Component.text("reloaded: ", NamedTextColor.GREEN)
            .append(
                Component.text(p.countProtected() + "/" + p.countAllOwned(), NamedTextColor.WHITE))
            .append(Component.text(" protected, mode: ", NamedTextColor.GREEN))
            .append(Component.text(plugin.getMode(), NamedTextColor.WHITE)));
  }

  private static void help(CommandSender sender, String cmd, String desc) {
    sender.sendMessage(
        Component.text("  /immortail " + cmd, NamedTextColor.YELLOW)
            .append(Component.text(" - " + desc, NamedTextColor.GRAY)));
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 1) {
      return filter(
          List.of("list", "info", "mode", "aggro", "pets", "defuse", "prune", "reload"), args[0]);
    }
    if (args.length == 2) {
      return switch (args[0].toLowerCase()) {
        case "list", "pets" -> onlinePlayers(args[1]);
        case "mode" -> filter(List.of("invulnerable", "resistance"), args[1]);
        case "aggro" -> {
          var options = new java.util.ArrayList<>(AggroHandler.SETTINGS);
          Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
          yield filter(options, args[1]);
        }
        default -> List.of();
      };
    }
    if (args.length == 3
        && args[0].equalsIgnoreCase("aggro")
        && AggroHandler.SETTINGS.contains(args[1].toLowerCase())) {
      return filter(List.of("true", "false"), args[2]);
    }
    return List.of();
  }
}
