package dev.dromzeh.immortail.listener;

import dev.dromzeh.immortail.Immortail;
import dev.dromzeh.immortail.protection.PermissionHelper;
import dev.dromzeh.immortail.protection.ProtectionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

public class EntityListener implements Listener {

  private final Immortail plugin;
  private final ProtectionManager protection;
  private final PermissionHelper permissions;

  public EntityListener(
      Immortail plugin, ProtectionManager protection, PermissionHelper permissions) {
    this.plugin = plugin;
    this.protection = protection;
    this.permissions = permissions;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onDamage(EntityDamageEvent event) {
    if (!plugin.getMode().equals("invulnerable")) return;
    if (!ProtectionManager.isOwned(event.getEntity())) return;
    if (protection.isProtected(event.getEntity())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onTarget(EntityTargetLivingEntityEvent event) {
    Entity entity = event.getEntity();
    var target = event.getTarget();
    if (target == null) return;

    if (protection.isProtected(entity) && ProtectionManager.isOwned(entity)) {
      boolean allowed;
      if (target instanceof Player) {
        allowed = permissions.isAggroAllowed(entity, "immortail.aggro.pvp", plugin.getAllowPvp());
      } else if (ProtectionManager.isOwned(target)) {
        allowed =
            permissions.isAggroAllowed(entity, "immortail.aggro.tamed", plugin.getAllowTamed());
      } else {
        allowed = permissions.isAggroAllowed(entity, "immortail.aggro.pve", plugin.getAllowPve());
      }
      if (!allowed) {
        event.setCancelled(true);
        return;
      }
    }

    if (protection.isProtected(target)
        && !(entity instanceof Player)
        && !plugin.getAllowMobRetaliation()) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onProtectedMobAttack(EntityDamageByEntityEvent event) {
    Entity damager = event.getDamager();
    Entity victim = event.getEntity();

    if (!ProtectionManager.isOwned(damager) || !protection.isProtected(damager)) return;

    if (victim instanceof Player
        && !permissions.isAggroAllowed(damager, "immortail.aggro.pvp", plugin.getAllowPvp())) {
      event.setCancelled(true);
      return;
    }
    if (ProtectionManager.isOwned(victim)
        && !permissions.isAggroAllowed(damager, "immortail.aggro.tamed", plugin.getAllowTamed())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onEntitiesLoad(EntitiesLoadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (ProtectionManager.isOwned(entity)) {
        protection.syncProtection(entity);
      }
    }
  }

  @EventHandler
  public void onEntityRemove(EntityRemoveEvent event) {
    // a chunk unloading is not a removal: the entity still exists, just not in memory
    if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) return;
    protection.untrack(event.getEntity());
  }

  @EventHandler
  public void onTame(EntityTameEvent event) {
    if (event.getEntity() instanceof Tameable tameable) {
      Bukkit.getScheduler()
          .runTaskLater(
              plugin,
              () -> {
                if (tameable.getOwner() != null) {
                  protection.syncProtection(event.getEntity());
                }
              },
              1L);
    }
  }
}
