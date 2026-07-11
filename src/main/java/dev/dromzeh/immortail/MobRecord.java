package dev.dromzeh.immortail;

import java.util.Objects;
import java.util.UUID;

public record MobRecord(UUID ownerUuid, String type, String name, ChunkRef lastChunk) {

  /**
   * Whether this and {@code other} describe the same protection (owner, type, name), ignoring
   * location.
   */
  public boolean sameProtectionAs(MobRecord other) {
    return other != null
        && ownerUuid.equals(other.ownerUuid)
        && Objects.equals(type, other.type)
        && Objects.equals(name, other.name);
  }
}
