package dev.dromzeh.immortail.command;

import static dev.dromzeh.immortail.command.CommandHelper.*;

import dev.dromzeh.immortail.protection.ProtectionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

class PruneHandler {

  private final ProtectionManager protection;

  PruneHandler(ProtectionManager protection) {
    this.protection = protection;
  }

  void execute(CommandSender sender) {
    if (!requireAdmin(sender)) return;
    if (protection.isPruning()) {
      sender.sendMessage(Component.text("a prune is already running", NamedTextColor.RED));
      return;
    }
    sender.sendMessage(label("pruning tracked mobs..."));
    protection.prune().thenAccept(result -> sendResult(sender, result));
  }

  private void sendResult(CommandSender sender, ProtectionManager.PruneResult result) {
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
}
