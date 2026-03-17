# immortail

protect your tamed (furry) friends from dying (in minecraft). tested only on paper 1.21.11.

![in game cmd ss](https://x.dromzeh.dev/ShareX/2026/03/javaw_bSIpb7bIM9.png)

<details>
  <summary>video demo</summary>

https://github.com/user-attachments/assets/d614b113-e895-4e77-bc51-8a4215669183

</details>

> [!NOTE]
> made this for personal use because no good options existed for permission-based tamed mob protection. i don't know java that well, this is whim work :3

## how it works

grant a player `immortail.protect` and their tamed mobs become truly invulnerable. revoke it and protection drops on the next sync cycle (every 5s).

- two protection modes, switchable at runtime:
  - **invulnerable** (default) - truly unkillable, blocks all damage including `/kill`
  - **resistance** - uses resistance 5 potion effect, `/kill` and void still work
- configurable aggression control - prevent protected mobs from griefing players
- per-player aggression overrides via [LuckPerms](https://luckperms.net/) permissions
- mob registry persists across chunk unloads and restarts
- instant protection on chunk load (no delay)
- tracks protected mobs via persistent data containers so it won't interfere with other plugins

## commands

all commands require `immortail.admin` (default: op).

| command | description |
| --- | --- |
| `/immortail` | help |
| `/immortail list` | all protected/unprotected mobs by type |
| `/immortail list <player>` | a player's mobs + permission status |
| `/immortail info` | plugin status, mode, aggro settings, mob counts |
| `/immortail mode [invulnerable\|resistance]` | view or change protection mode |
| `/immortail aggro` | view global aggression settings |
| `/immortail aggro <setting> <true\|false>` | change an aggression setting |
| `/immortail aggro <player>` | view a player's effective aggression (global + perm overrides) |
| `/immortail pets <player>` | list a player's mobs with names, UUIDs, status |
| `/immortail defuse` | immediately calm all angry protected mobs |
| `/immortail reload` | re-read config + force re-sync all mobs |

## permissions

| permission | description | default |
| --- | --- | --- |
| `immortail.protect` | protect all tamed mob types for this player/group | `false` |
| `immortail.protect.<type>` | protect a specific mob type only (e.g. `immortail.protect.wolf`) | `false` |
| `immortail.admin` | access to `/immortail` commands | `op` |
| `immortail.aggro.pvp` | this player's mobs can target/damage players (overrides config) | `false` |
| `immortail.aggro.tamed` | this player's mobs can target/damage other tamed mobs | `false` |
| `immortail.aggro.pve` | this player's mobs can target/damage hostile mobs | `false` |

all permissions work with both players and groups via LuckPerms.

## quick setup

protect all tamed mobs for everyone:

```
/lp group default permission set immortail.protect true
```

protect all tamed mobs for a specific player:

```
/lp user marcwl permission set immortail.protect true
```

protect only wolves for a group:

```
/lp group vip permission set immortail.protect.wolf true
```

## mob type filtering

by default, `immortail.protect` covers all tamed mob types. you can restrict which types are protected server-wide via config:

```yaml
# config.yml

# empty = all types protected (default)
protected-types: []

# or restrict to specific types
protected-types: [wolf, cat, parrot]
```

when `protected-types` is set, `immortail.protect` only covers those types. players/groups can still get additional types via `immortail.protect.<type>` permissions.

## aggression settings

by default, protected mobs cannot attack players or other tamed mobs (anti-grief), but can still fight hostile mobs.

```yaml
# config.yml
aggression:
  allow-pvp: false              # can protected mobs attack players?
  allow-tamed: false            # can protected mobs attack other tamed mobs?
  allow-pve: true               # can protected mobs attack hostile mobs?
  allow-mob-retaliation: true   # can hostile mobs target protected mobs?
```

per-player/group overrides via LuckPerms:

```
/lp group pvp permission set immortail.aggro.pvp true
/lp user marcwl permission set immortail.aggro.pvp true
```

## supported mobs

anything that implements bukkit's `Tameable` interface (wolves, cats, parrots, horses, donkeys, mules, llamas, camels, etc.) plus foxes via the trust system. arctic foxes are included under the `fox` type.

## building

requires java 21.

```sh
./gradlew build
```

jar outputs to `build/libs/immortail-v<version>.jar`.

## license

[MIT](https://dromzeh.mit-license.org)
