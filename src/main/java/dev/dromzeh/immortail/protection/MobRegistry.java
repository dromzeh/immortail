package dev.dromzeh.immortail.protection;

import dev.dromzeh.immortail.MobRecord;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public class MobRegistry {

  private final JavaPlugin plugin;
  private final Map<UUID, MobRecord> mobs = new HashMap<>();
  private boolean dirty = false;

  public MobRegistry(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public Map<UUID, MobRecord> getAll() {
    return Collections.unmodifiableMap(mobs);
  }

  public void load() {
    File file = new File(plugin.getDataFolder(), "mobs.yml");
    if (!file.exists()) return;
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    for (String key : yaml.getKeys(false)) {
      try {
        UUID entityUuid = UUID.fromString(key);
        UUID ownerUuid = UUID.fromString(yaml.getString(key + ".owner"));
        String type = yaml.getString(key + ".type");
        String name = yaml.getString(key + ".name");
        String world = yaml.getString(key + ".world");
        UUID worldUid = world != null ? UUID.fromString(world) : null;
        mobs.put(entityUuid, new MobRecord(ownerUuid, type, name, worldUid));
      } catch (Exception e) {
        plugin.getLogger().warning("invalid mob registry entry: " + key);
      }
    }
  }

  public void save() {
    if (!dirty) return;
    YamlConfiguration yaml = new YamlConfiguration();
    for (var entry : mobs.entrySet()) {
      String key = entry.getKey().toString();
      MobRecord record = entry.getValue();
      yaml.set(key + ".owner", record.ownerUuid().toString());
      yaml.set(key + ".type", record.type());
      if (record.name() != null) {
        yaml.set(key + ".name", record.name());
      }
      if (record.worldUid() != null) {
        yaml.set(key + ".world", record.worldUid().toString());
      }
    }
    try {
      yaml.save(new File(plugin.getDataFolder(), "mobs.yml"));
    } catch (IOException e) {
      plugin.getLogger().warning("failed to save mob registry: " + e.getMessage());
    }
    dirty = false;
  }

  public void forceSave() {
    dirty = true;
    save();
  }

  public void register(Entity entity, UUID ownerUuid) {
    String type = entity.getType().name().toLowerCase().replace("_", " ");
    String name = null;
    if (entity.customName() != null) {
      name = PlainTextComponentSerializer.plainText().serialize(entity.customName());
    }
    MobRecord record = new MobRecord(ownerUuid, type, name, entity.getWorld().getUID());
    if (!record.equals(mobs.get(entity.getUniqueId()))) {
      mobs.put(entity.getUniqueId(), record);
      dirty = true;
    }
  }

  public void unregister(UUID entityUuid) {
    if (mobs.remove(entityUuid) != null) {
      dirty = true;
    }
  }

  /**
   * Removes records whose world is no longer present (deleted or regenerated to a new UID). Records
   * with a null world (legacy entries written before worlds were tracked) are left untouched here —
   * they re-acquire a world UID the next time their entity loads and re-registers.
   *
   * @return number of records removed
   */
  public int pruneByWorlds(Set<UUID> liveWorldUids) {
    int before = mobs.size();
    mobs.values().removeIf(r -> r.worldUid() != null && !liveWorldUids.contains(r.worldUid()));
    int removed = before - mobs.size();
    if (removed > 0) {
      dirty = true;
    }
    return removed;
  }
}
