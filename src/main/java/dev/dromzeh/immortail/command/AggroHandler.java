package dev.dromzeh.immortail.command;

import static dev.dromzeh.immortail.command.CommandHelper.*;

import dev.dromzeh.immortail.Immortail;
import dev.dromzeh.immortail.protection.PermissionHelper;
import dev.dromzeh.immortail.protection.ProtectionManager;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

class AggroHandler {

  static final List<String> SETTINGS =
      List.of("allow-pvp", "allow-tamed", "allow-pve", "allow-mob-retaliation");

  private final Immortail plugin;
  private final ProtectionManager protection;
  private final PermissionHelper permissions;

  AggroHandler(Immortail plugin, ProtectionManager protection, PermissionHelper permissions) {
    this.plugin = plugin;
    this.protection = protection;
    this.permissions = permissions;
  }

  void execute(CommandSender sender, String[] args) {
    if (!requireAdmin(sender)) return;

    if (args.length == 1) {
      showAll(sender);
      return;
    }

    String arg1 = args[1].toLowerCase();

    if (SETTINGS.contains(arg1)) {
      handleSetting(sender, arg1, args);
      return;
    }

    showPlayer(sender, arg1);
  }

  private void showAll(CommandSender sender) {
    header(sender, "aggression settings");
    sender.sendMessage(bool("  allow-pvp: ", plugin.getAllowPvp()));
    sender.sendMessage(bool("  allow-tamed: ", plugin.getAllowTamed()));
    sender.sendMessage(bool("  allow-pve: ", plugin.getAllowPve()));
    sender.sendMessage(bool("  allow-mob-retaliation: ", plugin.getAllowMobRetaliation()));
    sender.sendMessage(Component.text(""));
  }

  private void handleSetting(CommandSender sender, String setting, String[] args) {
    if (args.length == 2) {
      sender.sendMessage(
          bool("  " + setting + ": ", plugin.getConfig().getBoolean("aggression." + setting)));
      return;
    }
    boolean val = Boolean.parseBoolean(args[2]);
    plugin.setAggroSetting(setting, val);
    protection.syncAll();
    sender.sendMessage(
        Component.text(setting + " set to: ", NamedTextColor.GREEN)
            .append(
                Component.text(
                    String.valueOf(val), val ? NamedTextColor.GREEN : NamedTextColor.RED)));
  }

  private void showPlayer(CommandSender sender, String playerName) {
    OfflinePlayer target = lookupPlayer(sender, playerName);
    if (target == null) return;

    header(sender, playerName + "'s aggression");

    boolean pvp = permissions.check(target, "immortail.aggro.pvp") || plugin.getAllowPvp();
    boolean tamed = permissions.check(target, "immortail.aggro.tamed") || plugin.getAllowTamed();
    boolean pve = permissions.check(target, "immortail.aggro.pve") || plugin.getAllowPve();

    sender.sendMessage(effective("  pvp: ", pvp, plugin.getAllowPvp()));
    sender.sendMessage(effective("  tamed: ", tamed, plugin.getAllowTamed()));
    sender.sendMessage(effective("  pve: ", pve, plugin.getAllowPve()));
    sender.sendMessage(Component.text(""));
  }
}
