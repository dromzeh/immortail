package dev.dromzeh.immortail.command;

import static dev.dromzeh.immortail.command.CommandHelper.*;

import dev.dromzeh.immortail.protection.MobRegistry;
import dev.dromzeh.immortail.protection.PermissionHelper;
import dev.dromzeh.immortail.protection.ProtectionManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

class ListHandler {

  private final ProtectionManager protection;
  private final MobRegistry registry;
  private final PermissionHelper permissions;

  ListHandler(ProtectionManager protection, MobRegistry registry, PermissionHelper permissions) {
    this.protection = protection;
    this.registry = registry;
    this.permissions = permissions;
  }

  void execute(CommandSender sender, String[] args) {
    if (!requireAdmin(sender)) return;

    if (args.length >= 2) {
      showPlayer(sender, args[1]);
      return;
    }

    header(sender, "protected mobs");

    Map<String, int[]> byType = new HashMap<>();
    for (Entity entity : protection.getAllOwned()) {
      String type = entityType(entity);
      byType.computeIfAbsent(type, k -> new int[] {0, 0});
      byType.get(type)[protection.isProtected(entity) ? 0 : 1]++;
    }

    for (var entry : byType.entrySet()) {
      int prot = entry.getValue()[0];
      int unprot = entry.getValue()[1];
      Component line =
          label("  " + entry.getKey() + ": ")
              .append(Component.text(String.valueOf(prot), NamedTextColor.GREEN))
              .append(Component.text(" protected", NamedTextColor.GRAY));
      if (unprot > 0) {
        line =
            line.append(Component.text(", ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(unprot), NamedTextColor.RED))
                .append(Component.text(" unprotected", NamedTextColor.GRAY));
      }
      sender.sendMessage(line);
    }

    sender.sendMessage(Component.text(""));
    kv(
        sender,
        "  loaded: ",
        protection.countProtected() + "/" + protection.countAllOwned() + " protected");
    kv(sender, "  registered: ", registry.getAll().size() + " mobs tracked");
  }

  private void showPlayer(CommandSender sender, String playerName) {
    OfflinePlayer target = lookupPlayer(sender, playerName);
    if (target == null) return;

    header(sender, playerName + "'s mobs");

    boolean hasPerm = permissions.ownerHasProtection(target);
    sender.sendMessage(
        label("  immortail.protect: ")
            .append(
                hasPerm
                    ? Component.text("granted", NamedTextColor.GREEN)
                    : Component.text("denied", NamedTextColor.RED)));
    sender.sendMessage(Component.text(""));

    List<Entity> all = protection.getAllOwnedByPlayer(target);
    List<Entity> prot = protection.getProtectedByPlayer(target);

    if (all.isEmpty()) {
      sender.sendMessage(Component.text("  no tamed mobs found (loaded)", NamedTextColor.GRAY));
      return;
    }

    Map<String, Integer> byType = new HashMap<>();
    for (Entity mob : all) {
      byType.merge(entityType(mob), 1, Integer::sum);
    }
    for (var entry : byType.entrySet()) {
      sender.sendMessage(
          label("  " + entry.getKey() + ": ")
              .append(Component.text(entry.getValue().toString(), NamedTextColor.WHITE)));
    }

    sender.sendMessage(Component.text(""));
    kv(sender, "  total: ", prot.size() + "/" + all.size() + " protected");
  }
}
