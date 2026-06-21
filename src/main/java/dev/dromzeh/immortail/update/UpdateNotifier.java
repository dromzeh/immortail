package dev.dromzeh.immortail.update;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Notifies admins on join when an update is available. */
public class UpdateNotifier implements Listener {

  private final UpdateChecker checker;

  public UpdateNotifier(UpdateChecker checker) {
    this.checker = checker;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (!player.hasPermission("immortail.admin")) {
      return;
    }
    UpdateStatus status = checker.status();
    if (!status.outdated()) {
      return;
    }
    Component notice = status.joinNotice();
    if (notice != null) {
      player.sendMessage(notice);
    }
  }
}
