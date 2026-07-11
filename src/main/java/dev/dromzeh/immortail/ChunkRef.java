package dev.dromzeh.immortail;

import java.util.UUID;

/** A reference to a specific chunk in a specific world — where a tracked mob was last seen. */
public record ChunkRef(UUID worldUid, int x, int z) {}
