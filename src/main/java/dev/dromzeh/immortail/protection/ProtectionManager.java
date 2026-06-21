package dev.dromzeh.immortail.protection;

import dev.dromzeh.immortail.ChunkRef;
import dev.dromzeh.immortail.Immortail;
import dev.dromzeh.immortail.MobRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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

  /** Runs a task on the server's main thread; used to marshal async chunk-load callbacks back. */
  private final Executor mainThread;

  private boolean pruning = false;

  public ProtectionManager(
      Immortail plugin,
      MobRegistry registry,
      PermissionHelper permissions,
      NamespacedKey protectedKey) {
    this.plugin = plugin;
    this.registry = registry;
    this.permissions = permissions;
    this.protectedKey = protectedKey;
    this.mainThread = task -> Bukkit.getScheduler().runTask(plugin, task);
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

  /**
   * Stops tracking an entity that has left the world for good (death, despawn, removal, ...). A
   * no-op when the entity was never tracked, so it is safe to call for any removed entity.
   */
  public void untrack(Entity entity) {
    registry.unregister(entity.getUniqueId());
  }

  public void syncAll() {
    streamOwned().forEach(this::syncProtection);

    int stale = registry.pruneByWorlds(liveWorldUids());
    if (stale > 0) {
      plugin.getLogger().info("pruned " + stale + " mob(s) from removed/regenerated worlds");
    }

    for (UUID uuid : List.copyOf(registry.getAll().keySet())) {
      Entity entity = Bukkit.getEntity(uuid);
      if (entity != null && !isProtected(entity)) {
        registry.unregister(uuid);
      }
    }

    registry.save();
  }

  /** Outcome of a {@link #prune()} run. {@code removed} includes the {@code offlineDeleted}. */
  public record PruneResult(int removed, int checked, int offlineDeleted) {}

  /** Whether a {@link #prune()} is in flight (its async chunk checks haven't resolved yet). */
  public boolean isPruning() {
    return pruning;
  }

  /**
   * Admin-triggered cleanup. Re-syncs loaded mobs first (so legacy records gain a location), then:
   *
   * <ul>
   *   <li>drops records whose world is gone (deleted or regenerated to a fresh UID);
   *   <li>drops legacy records that have no location and can't be seen loaded;
   *   <li>for mobs whose world exists but that aren't loaded, loads their last-known chunk and
   *       drops them only if they're genuinely missing — this catches mobs deleted while the server
   *       was offline, which fire no removal event.
   * </ul>
   *
   * The async chunk work and the registry mutation it feeds are marshalled back onto {@link
   * #mainThread}. Safe because the registry is a rebuildable cache — protection lives in each
   * entity's PDC, so any over-eager removal self-heals when the chunk reloads.
   */
  public CompletableFuture<PruneResult> prune() {
    pruning = true;
    streamOwned().forEach(this::syncProtection);

    int removed = registry.pruneByWorlds(liveWorldUids()); // worlds deleted/regenerated

    // group the remaining unloaded mobs by the chunk we'd load to confirm they still exist
    Map<ChunkRef, List<UUID>> byChunk = new HashMap<>();
    for (UUID uuid : List.copyOf(registry.getAll().keySet())) {
      MobRecord record = registry.getAll().get(uuid);
      if (record == null) continue;
      if (record.lastChunk() == null) {
        if (Bukkit.getEntity(uuid) == null) { // legacy record we can neither locate nor see
          registry.unregister(uuid);
          removed++;
        }
      } else if (Bukkit.getEntity(uuid)
          == null) { // world present but mob unloaded — verify on disk
        byChunk.computeIfAbsent(record.lastChunk(), c -> new ArrayList<>()).add(uuid);
      }
    }

    int removedBefore = removed;
    int checked = byChunk.values().stream().mapToInt(List::size).sum();
    return verifyMissing(byChunk)
        .thenApplyAsync(
            missing -> {
              missing.stream()
                  .filter(uuid -> Bukkit.getEntity(uuid) == null) // not reloaded mid-verification
                  .forEach(registry::unregister);
              registry.save();
              return new PruneResult(removedBefore + missing.size(), checked, missing.size());
            },
            mainThread)
        .whenComplete((result, error) -> pruning = false);
  }

  /**
   * Loads each candidate chunk once off the main thread and returns the mobs genuinely absent from
   * it. An unloaded entity hasn't moved since it was last seen, so its recorded chunk is where it
   * would be if it still existed. The entity scan and everything downstream run on the main thread;
   * a load failure is treated as "present" so we never remove on uncertainty.
   */
  private CompletableFuture<List<UUID>> verifyMissing(Map<ChunkRef, List<UUID>> byChunk) {
    List<CompletableFuture<List<UUID>>> checks = new ArrayList<>();
    for (var entry : byChunk.entrySet()) {
      ChunkRef ref = entry.getKey();
      List<UUID> candidates = entry.getValue();
      World world = Bukkit.getWorld(ref.worldUid());
      if (world == null) continue; // world unloaded since; keep its records
      if (!world.isChunkGenerated(ref.x(), ref.z())) {
        checks.add(CompletableFuture.completedFuture(candidates)); // chunk gone → mobs gone
        continue;
      }
      checks.add(
          world
              .getChunkAtAsync(ref.x(), ref.z(), false)
              .thenApplyAsync(chunk -> absentIn(chunk, candidates), mainThread)
              .exceptionally(error -> List.of())); // couldn't load → assume present
    }
    return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
        .thenApply(
            ignored ->
                checks.stream().flatMap(c -> c.join().stream()).collect(Collectors.toList()));
  }

  /** Of {@code candidates}, the UUIDs not present among the loaded chunk's entities. */
  private List<UUID> absentIn(Chunk chunk, List<UUID> candidates) {
    if (chunk == null) return List.of();
    Set<UUID> present = new HashSet<>();
    for (Entity entity : chunk.getEntities()) {
      present.add(entity.getUniqueId());
    }
    return candidates.stream().filter(uuid -> !present.contains(uuid)).collect(Collectors.toList());
  }

  private Set<UUID> liveWorldUids() {
    return Bukkit.getWorlds().stream().map(World::getUID).collect(Collectors.toSet());
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
