package dev.dromzeh.immortail.protection;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;

public class PermissionHelper {

  public boolean check(OfflinePlayer owner, String permission) {
    if (owner.isOnline() && owner.getPlayer() != null) {
      return owner.getPlayer().hasPermission(permission);
    }
    try {
      net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
      net.luckperms.api.model.user.User user = lp.getUserManager().getUser(owner.getUniqueId());
      if (user != null) {
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
      }
      lp.getUserManager().loadUser(owner.getUniqueId());
    } catch (Exception ignored) {
    }
    return false;
  }

  public boolean ownerHasProtection(OfflinePlayer owner) {
    return check(owner, "immortail.protect");
  }

  public boolean isAggroAllowed(Entity mob, String permission, boolean configDefault) {
    OfflinePlayer owner = ProtectionManager.getOwner(mob);
    if (owner != null && check(owner, permission)) {
      return true;
    }
    return configDefault;
  }
}
