package dev.dromzeh.immortail.command;

import static dev.dromzeh.immortail.command.CommandHelper.*;

import dev.dromzeh.immortail.MobRecord;
import dev.dromzeh.immortail.protection.MobRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

class PetsHandler {

  private final MobRegistry registry;

  PetsHandler(MobRegistry registry) {
    this.registry = registry;
  }

  void execute(CommandSender sender, String[] args) {
    if (!requireAdmin(sender)) return;

    if (args.length < 2) {
      sender.sendMessage(Component.text("usage: /immortail pets <player>", NamedTextColor.RED));
      return;
    }

    OfflinePlayer target = lookupPlayer(sender, args[1]);
    if (target == null) return;

    var playerMobs =
        registry.getAll().entrySet().stream()
            .filter(e -> e.getValue().ownerUuid().equals(target.getUniqueId()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    header(sender, args[1] + "'s pets");

    if (playerMobs.isEmpty()) {
      sender.sendMessage(Component.text("  no protected mobs registered", NamedTextColor.GRAY));
      sender.sendMessage(Component.text(""));
      return;
    }

    int loaded = 0;
    for (var entry : playerMobs.entrySet()) {
      UUID uuid = entry.getKey();
      MobRecord record = entry.getValue();
      Entity entity = Bukkit.getEntity(uuid);
      String shortId = uuid.toString().substring(0, 6);

      Component line = Component.text("  " + record.type(), NamedTextColor.GRAY);
      if (record.name() != null) {
        line = line.append(Component.text(" \"" + record.name() + "\"", NamedTextColor.WHITE));
      }
      line = line.append(Component.text(" (" + shortId + ")", NamedTextColor.DARK_GRAY));

      if (entity != null) {
        loaded++;
        if (entity instanceof Mob mob && mob.getTarget() != null) {
          line =
              line.append(
                  Component.text(
                      " - targeting " + entityType(mob.getTarget()), NamedTextColor.YELLOW));
        } else {
          line = line.append(Component.text(" - loaded", NamedTextColor.GREEN));
        }
      } else {
        line = line.append(Component.text(" - unloaded", NamedTextColor.RED));
      }

      sender.sendMessage(line);
    }

    sender.sendMessage(Component.text(""));
    sender.sendMessage(
        label("  total: ")
            .append(Component.text(playerMobs.size() + " registered", NamedTextColor.WHITE))
            .append(Component.text(", ", NamedTextColor.GRAY))
            .append(Component.text(loaded + " loaded", NamedTextColor.GREEN)));
    sender.sendMessage(Component.text(""));
  }
}
