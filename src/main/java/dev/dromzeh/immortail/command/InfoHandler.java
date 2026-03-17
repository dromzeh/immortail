package dev.dromzeh.immortail.command;

import static dev.dromzeh.immortail.command.CommandHelper.*;

import dev.dromzeh.immortail.Immortail;
import dev.dromzeh.immortail.protection.MobRegistry;
import dev.dromzeh.immortail.protection.ProtectionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

class InfoHandler {

  private final Immortail plugin;
  private final ProtectionManager protection;
  private final MobRegistry registry;

  InfoHandler(Immortail plugin, ProtectionManager protection, MobRegistry registry) {
    this.plugin = plugin;
    this.protection = protection;
    this.registry = registry;
  }

  void execute(CommandSender sender) {
    if (!requireAdmin(sender)) return;

    boolean luckperms;
    try {
      net.luckperms.api.LuckPermsProvider.get();
      luckperms = true;
    } catch (Exception e) {
      luckperms = false;
    }

    header(sender, "plugin info");
    kv(sender, "  version: ", plugin.getDescription().getVersion());
    kv(sender, "  author: ", String.join(", ", plugin.getDescription().getAuthors()));
    kv(sender, "  mode: ", plugin.getMode());
    sender.sendMessage(
        label("  luckperms: ")
            .append(
                luckperms
                    ? Component.text("detected", NamedTextColor.GREEN)
                    : Component.text("not found", NamedTextColor.RED)));
    sender.sendMessage(Component.text(""));
    kv(
        sender,
        "  loaded: ",
        protection.countProtected() + "/" + protection.countAllOwned() + " protected");
    kv(sender, "  registered: ", registry.getAll().size() + " total");
    sender.sendMessage(Component.text(""));
    sender.sendMessage(Component.text("  aggression:", NamedTextColor.GRAY));
    sender.sendMessage(bool("    allow-pvp: ", plugin.getAllowPvp()));
    sender.sendMessage(bool("    allow-tamed: ", plugin.getAllowTamed()));
    sender.sendMessage(bool("    allow-pve: ", plugin.getAllowPve()));
    sender.sendMessage(bool("    allow-mob-retaliation: ", plugin.getAllowMobRetaliation()));
    sender.sendMessage(Component.text(""));
  }
}
