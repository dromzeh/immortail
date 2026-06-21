package dev.dromzeh.immortail;

import dev.dromzeh.immortail.command.ImmortailCommand;
import dev.dromzeh.immortail.listener.EntityListener;
import dev.dromzeh.immortail.protection.MobRegistry;
import dev.dromzeh.immortail.protection.PermissionHelper;
import dev.dromzeh.immortail.protection.ProtectionManager;
import dev.dromzeh.immortail.update.UpdateChecker;
import dev.dromzeh.immortail.update.UpdateNotifier;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class Immortail extends JavaPlugin {

  private String mode;
  private boolean allowPvp;
  private boolean allowTamed;
  private boolean allowPve;
  private boolean allowMobRetaliation;
  private List<String> protectedTypes;

  private MobRegistry registry;
  private PermissionHelper permissions;
  private ProtectionManager protection;
  private UpdateChecker updateChecker;

  public String getMode() {
    return mode;
  }

  public boolean getAllowPvp() {
    return allowPvp;
  }

  public boolean getAllowTamed() {
    return allowTamed;
  }

  public boolean getAllowPve() {
    return allowPve;
  }

  public boolean getAllowMobRetaliation() {
    return allowMobRetaliation;
  }

  public List<String> getProtectedTypes() {
    return protectedTypes;
  }

  public MobRegistry getRegistry() {
    return registry;
  }

  public PermissionHelper getPermissions() {
    return permissions;
  }

  public ProtectionManager getProtection() {
    return protection;
  }

  public UpdateChecker getUpdateChecker() {
    return updateChecker;
  }

  public void setMode(String mode) {
    this.mode = mode;
    getConfig().set("mode", mode);
    saveConfig();
  }

  public void setAggroSetting(String key, boolean value) {
    getConfig().set("aggression." + key, value);
    saveConfig();
    reloadSettings();
  }

  @Override
  public void onEnable() {
    NamespacedKey protectedKey = new NamespacedKey(this, "protected");

    saveDefaultConfig();
    loadSettings();

    registry = new MobRegistry(this);
    registry.load();

    permissions = new PermissionHelper();
    protection = new ProtectionManager(this, registry, permissions, protectedKey);

    getServer()
        .getPluginManager()
        .registerEvents(new EntityListener(this, protection, permissions), this);

    ImmortailCommand cmd = new ImmortailCommand(this);
    getCommand("immortail").setExecutor(cmd);
    getCommand("immortail").setTabCompleter(cmd);

    Bukkit.getScheduler().runTaskTimer(this, protection::syncAll, 20L, 100L);

    updateChecker = new UpdateChecker(this);
    if (getConfig().getBoolean("check-for-updates", true)) {
      getServer().getPluginManager().registerEvents(new UpdateNotifier(updateChecker), this);
      updateChecker.checkAsync();
      long sixHours = 6L * 60L * 60L * 20L;
      Bukkit.getScheduler()
          .runTaskTimerAsynchronously(this, updateChecker::check, sixHours, sixHours);
    } else {
      updateChecker.disable();
    }

    getLogger().info("immortail loaded - mode: " + mode);
  }

  @Override
  public void onDisable() {
    registry.forceSave();
  }

  private void loadSettings() {
    mode = getConfig().getString("mode", "invulnerable");
    allowPvp = getConfig().getBoolean("aggression.allow-pvp", false);
    allowTamed = getConfig().getBoolean("aggression.allow-tamed", false);
    allowPve = getConfig().getBoolean("aggression.allow-pve", true);
    allowMobRetaliation = getConfig().getBoolean("aggression.allow-mob-retaliation", true);
    protectedTypes = getConfig().getStringList("protected-types");
  }

  public void reloadSettings() {
    reloadConfig();
    loadSettings();
  }
}
