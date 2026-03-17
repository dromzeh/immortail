package dev.dromzeh.immortail.protection;

import dev.dromzeh.immortail.Immortail;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ProtectionManager {

  private static final PotionEffect IMMORTAL_EFFECT =
      new PotionEffect(
          PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4, true, false, false);

  private final Immortail plugin;
  private final MobRegistry registry;
  private final PermissionHelper permissions;
  private final NamespacedKey protectedKey;

  public ProtectionManager(
      Immortail plugin,
      MobRegistry registry,
      PermissionHelper permissions,
      NamespacedKey protectedKey) {
    this.plugin = plugin;
    this.registry = registry;
    this.permissions = permissions;
    this.protectedKey = protectedKey;
  }

  public static boolean isOwned(Entity entity) {
    if (entity instanceof Tameable t) return t.isTamed() && t.getOwner() != null;
    if (entity instanceof Fox f) return f.getFirstTrustedPlayer() != null;
    return false;
  }

  public static OfflinePlayer getOwner(Entity entity) {
    if (entity instanceof Tameable t && t.isTamed() && t.getOwner() != null) {
      return (OfflinePlayer) t.getOwner();
    }
    if (entity instanceof Fox f && f.getFirstTrustedPlayer() != null) {
      return (OfflinePlayer) f.getFirstTrustedPlayer();
    }
    return null;
  }

  public NamespacedKey getProtectedKey() {
    return protectedKey;
  }

  public boolean isProtected(Entity entity) {
    return entity instanceof LivingEntity living
        && living.getPersistentDataContainer().has(protectedKey, PersistentDataType.BYTE);
  }

  private boolean isTypeAllowed(Entity entity, OfflinePlayer owner) {
    var types = plugin.getProtectedTypes();
    String entityType = entity.getType().name().toLowerCase();
    if (types.isEmpty()) return true;
    if (types.contains(entityType)) return true;
    return permissions.check(owner, "immortail.protect." + entityType);
  }

  public void syncProtection(Entity entity) {
    if (!isOwned(entity)) return;
    if (!(entity instanceof LivingEntity living)) return;

    OfflinePlayer owner = getOwner(entity);
    if (owner == null) return;

    var pdc = living.getPersistentDataContainer();

    if (permissions.ownerHasProtection(owner) && isTypeAllowed(entity, owner)) {
      if (!pdc.has(protectedKey, PersistentDataType.BYTE)) {
        pdc.set(protectedKey, PersistentDataType.BYTE, (byte) 1);
      }
      applyProtectionMode(entity, living);
      registry.register(entity, owner.getUniqueId());
      clearInvalidTargets(entity);
    } else {
      if (pdc.has(protectedKey, PersistentDataType.BYTE)) {
        pdc.remove(protectedKey);
        entity.setInvulnerable(false);
        living.removePotionEffect(PotionEffectType.RESISTANCE);
      }
      registry.unregister(entity.getUniqueId());
    }
  }

  private void applyProtectionMode(Entity entity, LivingEntity living) {
    if (plugin.getMode().equals("invulnerable")) {
      entity.setInvulnerable(true);
      if (living.hasPotionEffect(PotionEffectType.RESISTANCE)) {
        living.removePotionEffect(PotionEffectType.RESISTANCE);
      }
    } else {
      entity.setInvulnerable(false);
      if (!living.hasPotionEffect(PotionEffectType.RESISTANCE)) {
        living.addPotionEffect(IMMORTAL_EFFECT);
      }
    }
  }

  private void clearInvalidTargets(Entity entity) {
    if (!(entity instanceof Mob mob) || mob.getTarget() == null) return;

    var target = mob.getTarget();
    boolean targetIsPlayer = target instanceof Player;
    boolean targetIsOwned = isOwned(target);

    if (targetIsPlayer
        && !permissions.isAggroAllowed(entity, "immortail.aggro.pvp", plugin.getAllowPvp())) {
      mob.setTarget(null);
    } else if (targetIsOwned
        && !permissions.isAggroAllowed(entity, "immortail.aggro.tamed", plugin.getAllowTamed())) {
      mob.setTarget(null);
    } else if (!targetIsPlayer
        && !targetIsOwned
        && !permissions.isAggroAllowed(entity, "immortail.aggro.pve", plugin.getAllowPve())) {
      mob.setTarget(null);
    }
  }

  public void syncAll() {
    streamOwned().forEach(this::syncProtection);

    for (UUID uuid : List.copyOf(registry.getAll().keySet())) {
      Entity entity = Bukkit.getEntity(uuid);
      if (entity != null && !isProtected(entity)) {
        registry.unregister(uuid);
      }
    }

    registry.save();
  }

  public void defuseAll() {
    streamOwned()
        .filter(this::isProtected)
        .filter(entity -> entity instanceof Mob mob && mob.getTarget() != null)
        .forEach(entity -> ((Mob) entity).setTarget(null));
  }

  public Stream<Entity> streamOwned() {
    return Bukkit.getWorlds().stream()
        .flatMap(world -> world.getEntities().stream())
        .filter(ProtectionManager::isOwned);
  }

  public int countProtected() {
    return (int) streamOwned().filter(this::isProtected).count();
  }

  public int countAllOwned() {
    return (int) streamOwned().count();
  }

  public List<Entity> getAllOwned() {
    return streamOwned().collect(Collectors.toList());
  }

  public List<Entity> getProtectedByPlayer(OfflinePlayer player) {
    return streamOwned()
        .filter(
            entity -> {
              OfflinePlayer owner = getOwner(entity);
              return owner != null
                  && owner.getUniqueId().equals(player.getUniqueId())
                  && isProtected(entity);
            })
        .collect(Collectors.toList());
  }

  public List<Entity> getAllOwnedByPlayer(OfflinePlayer player) {
    return streamOwned()
        .filter(
            entity -> {
              OfflinePlayer owner = getOwner(entity);
              return owner != null && owner.getUniqueId().equals(player.getUniqueId());
            })
        .collect(Collectors.toList());
  }
}
