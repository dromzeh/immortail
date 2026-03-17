package dev.dromzeh.immortail.command;

import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

final class CommandHelper {

  private CommandHelper() {}

  static boolean requireAdmin(CommandSender sender) {
    if (sender.hasPermission("immortail.admin")) return true;
    sender.sendMessage(Component.text("no permission", NamedTextColor.RED));
    return false;
  }

  @SuppressWarnings("deprecation")
  static OfflinePlayer lookupPlayer(CommandSender sender, String name) {
    OfflinePlayer target = Bukkit.getOfflinePlayer(name);
    if (!target.hasPlayedBefore() && !target.isOnline()) {
      sender.sendMessage(Component.text("player not found: " + name, NamedTextColor.RED));
      return null;
    }
    return target;
  }

  static void header(CommandSender sender, String subtitle) {
    sender.sendMessage(Component.text(""));
    sender.sendMessage(
        Component.text("immortail", NamedTextColor.GREEN)
            .append(Component.text(" - " + subtitle, NamedTextColor.GRAY)));
    sender.sendMessage(Component.text(""));
  }

  static void kv(CommandSender sender, String key, String value) {
    sender.sendMessage(label(key).append(Component.text(value, NamedTextColor.WHITE)));
  }

  static Component label(String text) {
    return Component.text(text, NamedTextColor.GRAY);
  }

  static Component bool(String lbl, boolean value) {
    return label(lbl)
        .append(
            Component.text(
                String.valueOf(value), value ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  static Component effective(String lbl, boolean eff, boolean global) {
    Component line = bool(lbl, eff);
    if (eff != global) {
      line = line.append(Component.text(" (overridden)", NamedTextColor.YELLOW));
    }
    return line;
  }

  static String entityType(Entity entity) {
    return entity.getType().name().toLowerCase().replace("_", " ");
  }

  static List<String> filter(List<String> options, String prefix) {
    return options.stream()
        .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
        .collect(Collectors.toList());
  }

  static List<String> onlinePlayers(String prefix) {
    return Bukkit.getOnlinePlayers().stream()
        .map(p -> p.getName())
        .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
        .collect(Collectors.toList());
  }
}
