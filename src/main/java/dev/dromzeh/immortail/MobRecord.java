package dev.dromzeh.immortail;

import java.util.UUID;

public record MobRecord(UUID ownerUuid, String type, String name, UUID worldUid) {}
