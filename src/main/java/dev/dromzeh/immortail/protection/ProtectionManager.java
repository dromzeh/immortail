package dev.dromzeh.immortail.protection;

import dev.dromzeh.immortail.ChunkRef;
import dev.dromzeh.immortail.Immortail;
import dev.dromzeh.immortail.MobRecord;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ProtectionManager {

  private static final PotionEffect IMMORTAL_EFFECT =
      new PotionEffect(
          PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 4, true, false, false);

  /**
   * Who equipped a happy ghast's harness. Ghasts have no vanilla owner to read back, so we record
   * one ourselves in the entity's PDC — the same self-healing pattern as the protected flag. Built
   * from the raw namespace because {@link #isOwned}/{@link #getOwner} are static.
   */
  private static final NamespacedKey HARNESS_OWNER_KEY =
      new NamespacedKey("immortail", "harness-owner");

  private final Immortail plugin;
  private final MobRegistry registry;
  private final PermissionHelper permissions;
  private final NamespacedKey protectedKey;

  /** Runs a task on the server's main thread; used to marshal async chunk-load callbacks back. */
  private final Executor mainThread;

  private boolean pruning = false;

  /** World uids read from uid.dat files in the world container; lazily (re)scanned and cached. */
  private final Set<UUID> diskWorldUids = new HashSet<>();

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
    if (entity instanceof HappyGhast g) return hasHarness(g) && harnessOwner(g) != null;
    return false;
  }

  public static OfflinePlayer getOwner(Entity entity) {
    if (entity instanceof Tameable t && t.isTamed() && t.getOwner() != null) {
      return (OfflinePlayer) t.getOwner();
    }
    if (entity instanceof Fox f && f.getFirstTrustedPlayer() != null) {
      return (OfflinePlayer) f.getFirstTrustedPlayer();
    }
    if (entity instanceof HappyGhast g && hasHarness(g)) {
      UUID owner = harnessOwner(g);
      if (owner != null) return Bukkit.getOfflinePlayer(owner);
    }
    return null;
  }

  private static boolean hasHarness(HappyGhast ghast) {
    EntityEquipment equipment = ghast.getEquipment();
    return equipment != null && !equipment.getItem(EquipmentSlot.BODY).getType().isAir();
  }

  private static UUID harnessOwner(HappyGhast ghast) {
    String uuid =
        ghast.getPersistentDataContainer().get(HARNESS_OWNER_KEY, PersistentDataType.STRING);
    if (uuid == null) return null;
    try {
      return UUID.fromString(uuid);
    } catch (IllegalArgumentException e) {
      return null;
    }
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
      removeProtection(living);
    }
  }

  /**
   * Called a tick after a player interacts with a happy ghast, once the click's outcome (harness
   * equipped, harness sheared off, mounted) is visible. The player who equips the harness — or the
   * first to interact with a harnessed, unclaimed one (e.g. dispenser-equipped) — is recorded as
   * owner; the harness coming off ends ownership.
   */
  public void syncHappyGhast(HappyGhast ghast, Player player) {
    if (!hasHarness(ghast)) {
      ghast.getPersistentDataContainer().remove(HARNESS_OWNER_KEY);
      removeProtection(ghast);
      return;
    }
    if (harnessOwner(ghast) == null) {
      ghast
          .getPersistentDataContainer()
          .set(HARNESS_OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
    }
    syncProtection(ghast);
  }

  private void removeProtection(LivingEntity living) {
    var pdc = living.getPersistentDataContainer();
    if (pdc.has(protectedKey, PersistentDataType.BYTE)) {
      pdc.remove(protectedKey);
      living.setInvulnerable(false);
      living.removePotionEffect(PotionEffectType.RESISTANCE);
    }
    registry.unregister(living.getUniqueId());
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

    int stale = registry.pruneByWorlds(presentWorldUids());
    if (stale > 0) {
      plugin.getLogger().info("pruned " + stale + " mob(s) from removed/regenerated worlds");
    }

    for (UUID uuid : List.copyOf(registry.getAll().keySet())) {
      Entity entity = Bukkit.getEntity(uuid);
      if (entity == null) continue;
      if (!isOwned(entity) && entity instanceof LivingEntity living) {
        // ownership can end without an event we see (untamed by another plugin, harness gone):
        // revoke rather than orphan an invulnerable mob — syncProtection never reaches its
        // removal branch for unowned entities
        if (entity instanceof HappyGhast ghast) {
          ghast.getPersistentDataContainer().remove(HARNESS_OWNER_KEY);
        }
        removeProtection(living);
      } else if (!isProtected(entity)) {
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
    try {
      streamOwned().forEach(this::syncProtection);

      diskWorldUids.clear(); // a manual prune answers with a fresh look at disk, not the cache
      int removed = registry.pruneByWorlds(presentWorldUids()); // worlds deleted/regenerated

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
                List<UUID> stillGone =
                    missing.stream()
                        .filter(uuid -> Bukkit.getEntity(uuid) == null) // reloaded mid-verification
                        .collect(Collectors.toList());
                stillGone.forEach(registry::unregister);
                registry.save();
                return new PruneResult(removedBefore + stillGone.size(), checked, stillGone.size());
              },
              mainThread)
          .whenComplete((result, error) -> pruning = false);
    } catch (RuntimeException e) {
      pruning = false; // don't wedge the command when the synchronous half throws
      throw e;
    }
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

  /**
   * The uids of every world that still exists: loaded worlds plus world folders on disk. An
   * unloaded world keeps its uid.dat, so unloading one (e.g. via a world-management plugin) is not
   * existence loss — only deleting or regenerating the folder is. The disk scan only runs when a
   * record references a uid we can't otherwise account for, and its result is cached.
   */
  private Set<UUID> presentWorldUids() {
    Set<UUID> present = new HashSet<>();
    Bukkit.getWorlds().forEach(world -> present.add(world.getUID()));
    present.addAll(diskWorldUids);
    boolean unknown =
        registry.getAll().values().stream()
            .anyMatch(r -> r.lastChunk() != null && !present.contains(r.lastChunk().worldUid()));
    if (unknown) {
      rescanDiskWorlds();
      present.addAll(diskWorldUids);
    }
    return present;
  }

  private void rescanDiskWorlds() {
    diskWorldUids.clear();
    File[] folders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
    if (folders == null) return;
    for (File folder : folders) {
      File uidFile = new File(folder, "uid.dat");
      if (!uidFile.isFile()) continue;
      try (DataInputStream in = new DataInputStream(new FileInputStream(uidFile))) {
        diskWorldUids.add(new UUID(in.readLong(), in.readLong()));
      } catch (IOException e) {
        // unreadable uid.dat: treat the world as absent; worst case records self-heal on load
      }
    }
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
