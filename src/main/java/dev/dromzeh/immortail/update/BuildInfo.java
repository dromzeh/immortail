package dev.dromzeh.immortail.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.bukkit.plugin.java.JavaPlugin;

/** Build metadata baked into the jar by gradle (see build.gradle.kts generateBuildInfo). */
public record BuildInfo(String version, String commit, String commitShort, String branch) {

  static final String UNKNOWN = "unknown";

  static BuildInfo load(JavaPlugin plugin) {
    Properties props = new Properties();
    try (InputStream in = plugin.getResource("immortail-build.properties")) {
      if (in != null) {
        props.load(in);
      }
    } catch (IOException ignored) {
      // fall back to plugin.yml version + unknown commit below
    }
    return new BuildInfo(
        props.getProperty("version", plugin.getDescription().getVersion()),
        props.getProperty("commit", UNKNOWN),
        props.getProperty("commit.short", UNKNOWN),
        props.getProperty("branch", UNKNOWN));
  }

  boolean hasCommit() {
    return commit != null && !commit.isBlank() && !commit.equals(UNKNOWN);
  }
}
